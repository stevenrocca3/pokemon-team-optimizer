"""
Shield economy and switching.

- Shields: each shield you used is judged by the damage the move would have done (the battle engine's formula at
  default league IVs, no buffs) against your HP when it was thrown. Wasteful: you'd easily have survived (50%+ left),
  or you'd have survived (30%+ left) a move that was resisted or a cheap bait (45 energy or less).
- Switch lag: more than 3 turns (on screen, not counting cut-scenes) in a hard-counter matchup, with your switch ready
  and someone to switch to. Hard counter: they put out 1.5x+ your pressure (share of HP per turn from fast moves plus
  the charged-move damage their energy buys), and the HP bars agree you were losing the exchange.
- Catches: a charged move landing on a Pokémon you switched in within 4 turns, which takes it better than the one you
  switched out (caught); or a super-effective charged move landing while a resister sat on your bench with your
  switch ready for the 6 turns before (missed).
"""
from battle_analysis import gamedata
from battle_analysis.analysis.alignment import infer_fast_move
from battle_analysis.analysis.context import MatchContext, has_event

HARD_COUNTER = 1.5
LAG_TURNS = 3


def _opp_move(ctx: MatchContext, i: int) -> gamedata.Move | None:
    move_id = ctx.turns[i].get('move_id')
    return gamedata.moves().get(move_id) if move_id else None


def audit_shields(log: dict) -> dict:
    ctx = MatchContext(log)
    shields = []
    for i in ctx.throws('opp'):
        e = ctx.turns[i]
        if not e.get('shield_used'):
            continue
        move = _opp_move(ctx, i)
        user, opp = ctx.species('user', e['user_active']), ctx.species('opp', e['opp_active'])
        hp = e.get('user_hp_pct')
        s = {'turn': e['turn'], 'timestamp_ms': e['timestamp_ms'], 'move_name': move.name if move else None,
             'user': user, 'opp': opp, 'hp_before_pct': hp, 'damage_pct': None, 'verdict': 'UNKNOWN',
             'message': f"Turn {e['turn']}: shielded a charged move that couldn't be identified"}
        dmg = ctx.damage_pct(move, opp, user) if move and user and opp else None
        if dmg is not None and hp is not None:
            left = hp / 100 - dmg
            eff = gamedata.effectiveness(move.type, gamedata.species()[user].types)
            s['damage_pct'] = round(100 * dmg, 1)
            resisted = eff <= 0.625
            why = ', resisted' if resisted else (f', a {move.energy}-energy bait' if move.energy <= 45 else '')
            if left >= 0.5 or (left >= 0.3 and why):
                s |= {'verdict': 'WASTEFUL', 'message': f"Turn {e['turn']}: wasted a shield on {move.name}{why}. "
                      f"It would have done {100 * dmg:.0f}% and left {user} on {100 * left:.0f}%."}
            else:
                outcome = 'a knock-out' if left <= 0 else f'{user} down to {100 * left:.0f}%'
                s |= {'verdict': 'JUSTIFIED', 'message': f"Turn {e['turn']}: shield on {move.name} was right: "
                      f"unshielded it was {outcome}."}
        shields.append(s)
    return {'shields': shields, 'used': len(shields), 'mismanaged': sum(s['verdict'] == 'WASTEFUL' for s in shields)}


def _pressure(ctx: MatchContext, attacker: str, defender: str, fast: gamedata.Move, charged: list) -> float | None:
    """
    Share of the defender's HP the attacker puts out per turn: its fast move's damage per turn, plus the energy that
    fast move builds per turn spent on its most damage-per-energy charged move (no shields, default IVs).
    """
    fast_dmg = ctx.damage_pct(fast, attacker, defender)
    if fast_dmg is None:
        return None
    dpe = [d / m.energy for m in charged if m.energy and (d := ctx.damage_pct(m, attacker, defender)) is not None]
    return fast_dmg / fast.turns + (fast.energy_gain / fast.turns) * (max(dpe) if dpe else 0)


def _pressure_ratio(ctx: MatchContext, user_hud: str, opp_hud: str) -> float | None:
    """Their pressure on you over yours on them; None when either side's moves are unknown."""
    user, opp = ctx.species('user', user_hud), ctx.species('opp', opp_hud)
    user_fast = ctx.fast_move('user', user_hud)
    hits = [x['turn'] for x in ctx.turns if x['opp_active'] == opp_hud and has_event(x, 'OPP_FAST_MOVE_HIT')]
    opp_fast = infer_fast_move(opp, hits) if opp else None
    if not (user_fast and opp_fast):
        return None
    mine = _pressure(ctx, user, opp, user_fast, ctx.charged_moves('user', user_hud))
    theirs = _pressure(ctx, opp, user, opp_fast, ctx.charged_moves('opp', opp_hud))
    if not mine or theirs is None:
        return None
    return theirs / mine


def _hp_fall(ctx: MatchContext, side: str, lo: int, hi: int) -> float | None:
    """How far one side's HP fell over turns [lo, hi), from the readings (None without two)."""
    known = [ctx.turns[k][f'{side}_hp_pct'] for k in range(lo, hi) if ctx.turns[k].get(f'{side}_hp_pct') is not None]
    return known[0] - known[-1] if len(known) >= 2 else None


def audit_switch_lag(log: dict) -> dict:
    ctx = MatchContext(log)
    lags = []
    i = 0
    while i < len(ctx.turns):
        e = ctx.turns[i]
        pair = (e['user_active'], e['opp_active'])
        j = i
        while j < len(ctx.turns) and (ctx.turns[j]['user_active'], ctx.turns[j]['opp_active']) == pair:
            j += 1
        ratio = _pressure_ratio(ctx, *pair) if all(pair) else None
        mine, theirs = _hp_fall(ctx, 'user', i, j), _hp_fall(ctx, 'opp', i, j)
        # the estimate is overruled by what the HP bars showed: no lag if you were winning the exchange
        winning = mine is not None and theirs is not None and mine <= theirs
        if ratio is not None and ratio >= HARD_COUNTER and not winning:
            # the turns you could have switched on: switch ready, a teammate left, HUD up (not a cut-scene)
            free = [k for k in range(i, j) if ctx.turns[k]['hud_visible'] and (ctx.turns[k].get('switch_clock_s') or 0) == 0
                    and (ctx.turns[k].get('user_remaining') or 0) > 1]
            if len(free) > LAG_TURNS:
                at = free[LAG_TURNS]
                lags.append({
                    'turn': ctx.turns[at]['turn'], 'timestamp_ms': ctx.turns[at]['timestamp_ms'],
                    'user': ctx.species('user', pair[0]), 'opp': ctx.species('opp', pair[1]),
                    'turns_stayed': len(free), 'pressure_ratio': round(ratio, 2),
                    'message': f"Turn {ctx.turns[at]['turn']}: stayed in {len(free)} turns with {pair[0]} into "
                               f"{pair[1]}, a hard counter ({ratio:.1f}x your damage pressure), with your switch ready.",
                })
        i = j
    return {'lags': lags, 'lag_turns': sum(lag['turns_stayed'] for lag in lags)}


def audit_catches(log: dict) -> dict:
    ctx = MatchContext(log)
    team = log.get('user_team') or []
    catches = []
    for i in ctx.throws('opp'):
        e = ctx.turns[i]
        move = _opp_move(ctx, i)
        user_hud = e['user_active']
        user, opp = ctx.species('user', user_hud), ctx.species('opp', e['opp_active'])
        if not (move and user and opp):
            continue
        eff = gamedata.effectiveness(move.type, gamedata.species()[user].types)
        switch_at = next((k for k in range(i, max(-1, i - 5), -1) if has_event(ctx.turns[k], 'SWITCH')), None)
        if switch_at is not None and switch_at > 0:
            previous_hud = ctx.turns[switch_at - 1]['user_active']
            previous = ctx.species('user', previous_hud)
            if previous and previous != user:
                before = gamedata.effectiveness(move.type, gamedata.species()[previous].types)
                if eff < before:
                    catches.append({'turn': e['turn'], 'timestamp_ms': e['timestamp_ms'], 'verdict': 'CAUGHT',
                                    'move_name': move.name, 'caught_on': user, 'instead_of': previous,
                                    'message': f"Turn {e['turn']}: caught {move.name} on {user} instead of {previous}."})
                    continue
        if eff < 1.6:
            continue
        fainted = {ctx.turns[k]['user_active'] for k in range(i) if has_event(ctx.turns[k], 'FAINT')}
        bench = [s for s in team if s != user_hud and s not in fainted]
        ready = all((ctx.turns[k].get('switch_clock_s') or 0) == 0 for k in range(max(0, i - 6), i + 1))
        resists = {s: gamedata.effectiveness(move.type, gamedata.species()[sp].types)
                   for s in bench if (sp := ctx.species('user', s)) in gamedata.species()}
        resisters = sorted((s for s, x in resists.items() if x <= 0.625), key=lambda s: resists[s])
        if ready and resisters:
            better = resisters[0]  # the one that takes it best
            catches.append({'turn': e['turn'], 'timestamp_ms': e['timestamp_ms'], 'verdict': 'MISSED',
                            'move_name': move.name, 'took_it': user, 'better': better,
                            'message': f"Turn {e['turn']}: {user} took a super-effective {move.name} while {better}, "
                                       f"which resists it, sat on the bench with your switch ready."})
    return {'catches': catches, 'caught': sum(c['verdict'] == 'CAUGHT' for c in catches),
            'missed': sum(c['verdict'] == 'MISSED' for c in catches)}

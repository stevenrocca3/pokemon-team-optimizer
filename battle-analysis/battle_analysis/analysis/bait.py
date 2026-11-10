"""
Charged move choice and shield baiting, for each of your throws:

- NUKE_INTO_SHIELD: your dearer move went into a shield while a move 10+ energy cheaper was available; the shield
  would have gone on the cheap one just the same, and the energy difference is lost.
- MISSED_BAIT: the same in the endgame (your last Pokémon), and that Pokémon fainted before throwing again: baiting
  first was what the win needed.
- LOWER_DAMAGE_CHOICE: no shields left on their side, you threw the weaker move while you had the energy for one doing
  5+ points more of their HP, and the weaker one didn't knock them out anyway.
Your moves are the ones seen thrown, made up to two from the species' usual moveset.
"""
from battle_analysis import gamedata
from battle_analysis.analysis.context import MatchContext, has_event


def _energy_before(ctx: MatchContext, i: int) -> int | None:
    """Your energy as last read just before (or at) the throw."""
    for k in range(i, max(-1, i - 4), -1):
        if ctx.turns[k].get('user_energy') is not None:
            return ctx.turns[k]['user_energy']
    return None


def audit_move_choice(log: dict) -> dict:
    ctx = MatchContext(log)
    moves = gamedata.moves()
    throws = ctx.throws('user')
    findings = []
    for n, i in enumerate(throws):
        e = ctx.turns[i]
        thrown = moves.get(e.get('move_id') or '')
        if not thrown or not e['user_active']:
            continue
        user, opp = ctx.species('user', e['user_active']), ctx.species('opp', e['opp_active'])
        others = [m for m in ctx.charged_moves('user', e['user_active']) if m.id != thrown.id]
        base = {'turn': e['turn'], 'timestamp_ms': e['timestamp_ms'], 'move_name': thrown.name}
        if e.get('opp_shield_used'):
            cheaper = [m for m in others if m.energy <= thrown.energy - 10]
            if cheaper:
                bait = min(cheaper, key=lambda m: m.energy)
                next_throw = throws[n + 1] if n + 1 < len(throws) else len(ctx.turns)
                fainted = any(has_event(ctx.turns[k], 'FAINT') for k in range(i + 1, next_throw))
                if (e.get('user_remaining') or 0) == 1 and fainted:
                    findings.append(base | {'kind': 'MISSED_BAIT', 'better_move': bait.name, 'message':
                        f"Turn {e['turn']}: endgame, threw {thrown.name} into a shield and {e['user_active']} went down "
                        f"before throwing again. Baiting with {bait.name} ({bait.energy} energy) first was the win."})
                else:
                    findings.append(base | {'kind': 'NUKE_INTO_SHIELD', 'better_move': bait.name, 'message':
                        f"Turn {e['turn']}: threw {thrown.name} ({thrown.energy} energy) into a shield. {bait.name} "
                        f"({bait.energy} energy) would have drawn it for {thrown.energy - bait.energy} less."})
            continue
        if e.get('opp_shields') == 0 and user and opp:
            dealt = ctx.damage_pct(thrown, user, opp)
            hp = e.get('opp_hp_pct')
            energy = _energy_before(ctx, i)
            if dealt is None or (hp is not None and 100 * dealt >= hp):
                continue
            for alt in others:
                alt_dmg = ctx.damage_pct(alt, user, opp)
                affordable = alt.energy <= thrown.energy or (energy is not None and energy >= alt.energy)
                if alt_dmg is not None and affordable and 100 * (alt_dmg - dealt) >= 5:
                    findings.append(base | {'kind': 'LOWER_DAMAGE_CHOICE', 'better_move': alt.name, 'message':
                        f"Turn {e['turn']}: threw {thrown.name} ({100 * dealt:.1f}%) into no shields with energy for "
                        f"{alt.name} ({100 * alt_dmg:.1f}%)."})
                    break
    return {'findings': findings, 'suboptimal_charges': len(findings)}

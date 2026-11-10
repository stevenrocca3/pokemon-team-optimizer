"""
Fast move alignment: did your charged moves give the opponent a free fast move?

The opponent's fast move takes d turns (1-5) and registers on its last turn. Counting turns from its last
registration (or from the matchup / the last cut-scene when none was seen), a charged move thrown on turn k with
k % d == 0 lands on the same turn their fast move completes, so that fast move goes through anyway: a free fast move
(and its energy) for them, d turns' worth. Any other turn is off alignment and denies it. A 1-turn move registers
every turn, so it has no alignment to play around.

The opponent's fast move isn't on screen; it's inferred from how often your HP drops while they're out (the most
common gap between hits), among the moves that species (in the right form) can learn.
"""
from collections import Counter
from collections.abc import Sequence

from battle_analysis import gamedata
from battle_analysis.analysis.context import MatchContext, has_event


def infer_fast_move(species: str, hit_turns: Sequence[int]) -> gamedata.Move | None:
    sp = gamedata.species().get(species)
    moves = gamedata.moves()
    recommended = gamedata.moveset_for(species)[:1]
    if not sp:
        return None
    learnable = [moves[m] for m in sp.fast_moves if m in moves]
    default = moves.get(recommended[0]) if recommended else (learnable[0] if learnable else None)
    # the HP bar drains over ~1 s, so one hit can show as drops on consecutive turns: a run of 2-3 is one hit (at its
    # start); only runs of 4+ turns of unbroken drops are a 1-turn move hitting every turn
    runs: list[list[int]] = []
    for t in sorted(set(hit_turns)):
        if runs and t - runs[-1][-1] == 1:
            runs[-1].append(t)
        else:
            runs.append([t])
    if runs and max(len(r) for r in runs) >= 4:
        gaps = [1]
    else:
        starts = [r[0] for r in runs]
        gaps = [b - a for a, b in zip(starts, starts[1:])]
    if not gaps or not learnable:
        return default
    counts = Counter(gaps)
    gap = min(counts, key=lambda g: (-counts[g], g))  # the most common gap; the shorter one on a tie
    candidates = sorted(learnable, key=lambda m: (abs(m.turns - gap), m.id not in recommended))
    return candidates[0]


def _reference(ctx: MatchContext, i: int, hits: list[int]) -> tuple[int, bool]:
    """
    Where the opponent's current fast-move cycle started counting for the throw at index i: their last hit since
    the matchup began and since the last cut-scene (True: k = i - hit), else the first turn after those (False:
    that turn is turn 1).
    """
    start = ctx.matchup_start(i)
    earlier = [p for p in ctx.throws() if start <= p < i]
    if earlier:
        p = earlier[-1]
        j = p + 1
        while j < i and ctx.turns[j]['hud_visible']:
            j += 1  # the cut-scene comes just after the throw
        while j < i and not ctx.turns[j]['hud_visible']:
            j += 1
        start = max(start, min(j, i))
    recent = [h for h in hits if start <= h < i]
    if recent:
        return recent[-1], True
    return start, False


def audit_alignment(log: dict) -> dict:
    ctx = MatchContext(log)
    throws = []
    fast_by_opp: dict[str, gamedata.Move | None] = {}
    for i in ctx.throws('user'):
        e = ctx.turns[i]
        opp_hud = e['opp_active']
        opp = ctx.species('opp', opp_hud)
        hits = [j for j, x in enumerate(ctx.turns) if x['opp_active'] == opp_hud and has_event(x, 'OPP_FAST_MOVE_HIT')]
        if opp_hud not in fast_by_opp:
            fast_by_opp[opp_hud] = infer_fast_move(opp, [ctx.turns[j]['turn'] for j in hits]) if opp else None
        fast = fast_by_opp[opp_hud]
        ref, from_hit = _reference(ctx, i, hits)
        k = e['turn'] - ctx.turns[ref]['turn'] + (0 if from_hit else 1)
        t = {'turn': e['turn'], 'timestamp_ms': e['timestamp_ms'], 'opp': opp, 'move_name': e.get('move_name'),
             'opp_fast_move': fast.name if fast else None, 'move_turns': fast.turns if fast else None,
             'turn_in_cycle': k, 'free_fast_moves': 0, 'turns_granted': 0,
             # counted from a hit seen within two cycles: solid; otherwise a missed reading could shift the count
             'confidence': 'high' if from_hit and fast and k <= 2 * fast.turns else 'low'}
        if not fast:
            t |= {'verdict': 'UNKNOWN', 'message': f"Turn {e['turn']}: couldn't tell the opponent's fast move"}
        elif fast.turns == 1:
            t |= {'verdict': 'NO_ALIGNMENT', 'message': f"Turn {e['turn']}: {fast.name} is a 1-turn move, no alignment to play"}
        elif k % fast.turns == 0:
            t |= {'verdict': 'TIMING_ERROR', 'free_fast_moves': 1, 'turns_granted': fast.turns,
                  'message': f"Turn {e['turn']}: Threw Charge Move on {fast.turns}-turn alignment "
                             f"(+1 free {fast.name} given to opponent)"}
        else:
            t |= {'verdict': 'OPTIMAL',
                  'message': f"Turn {e['turn']}: Threw off alignment (turn {k} of a {fast.turns}-turn {fast.name}), "
                             f"denying the opponent a fast move"}
        throws.append(t)
    errors = [t for t in throws if t['verdict'] == 'TIMING_ERROR']
    return {
        'throws': throws,
        'timing_errors': len(errors),
        'free_turns_given': sum(t['turns_granted'] for t in errors),
        'free_fast_moves_given': sum(t['free_fast_moves'] for t in errors),
        'optimal_throws': sum(t['verdict'] == 'OPTIMAL' for t in throws),
    }

"""
The match state reconstructor: per-turn HUD readings in, a cleaned turn_log out.

Readings are noisy (OCR misses, effects covering the HUD, cut-scenes hiding it for seconds), so this cleans them with
what can't change mid-battle before deriving actions:
- the species out on each side only changes on a confirmed switch (detection._active_species);
- Poké Balls and shields only go down: a median over neighbouring readings, then never rising;
- HP only goes down for the same Pokémon (GBL has no healing moves), even across switching out and back in;
- energy comes from the button fill times that button's move cost.
Then the actions: charged moves from the cut-scene gaps (with the "X used Y!" / "Attack incoming!" banners and the
shield prompt saying whose), shields from the shield counts dropping, fast-move hits from HP dropping between
readings, switches and faints from detection.detect_events, and the 50 s switch timer from your last switch.
"""
import copy
from collections import Counter
from collections.abc import Sequence

from battle_analysis import gamedata
from battle_analysis.cv.detection import AttackIncoming, MoveBanner, _active_species, detect_events
from battle_analysis.cv.model import SideReading, TurnReading
from battle_analysis.cv.video import TURN_MS

SWITCH_TIMER_S = 50
ACTIONS = ['FAINT', 'OPP_FAINT', 'SWITCH', 'OPP_SWITCH', 'FORCED_SWITCH', 'OPP_FORCED_SWITCH', 'CHARGE_MOVE_THROWN',
           'OPP_CHARGE_MOVE_THROWN', 'FAST_MOVE_HIT', 'OPP_FAST_MOVE_HIT', 'NONE']

_nullable_num = {'type': ['number', 'null']}
_nullable_str = {'type': ['string', 'null']}
TURN_LOG_SCHEMA = {
    '$schema': 'https://json-schema.org/draft/2020-12/schema',
    'type': 'object',
    'required': ['match_id', 'user_team', 'opponent_team', 'turn_log'],
    'properties': {
        'match_id': {'type': 'string'},
        'user_team': {'type': 'array', 'items': {'type': 'string'}, 'maxItems': 3},
        'opponent_team': {'type': 'array', 'items': {'type': 'string'}, 'maxItems': 3},
        'turn_log': {'type': 'array', 'items': {
            'type': 'object',
            'required': ['turn', 'timestamp_ms', 'user_active', 'opp_active', 'user_hp_pct', 'opp_hp_pct',
                         'user_energy', 'opp_energy', 'action_detected', 'move_name', 'shield_used'],
            'properties': {
                'turn': {'type': 'integer', 'minimum': 0},
                'timestamp_ms': {'type': 'integer', 'minimum': 0},
                'user_active': _nullable_str, 'opp_active': _nullable_str,
                'user_hp_pct': {'type': ['number', 'null'], 'minimum': 0, 'maximum': 100},
                'opp_hp_pct': {'type': ['number', 'null'], 'minimum': 0, 'maximum': 100},
                'user_energy': {'type': ['integer', 'null'], 'minimum': 0, 'maximum': 100},
                'opp_energy': {'type': ['integer', 'null'], 'minimum': 0, 'maximum': 100},
                'action_detected': {'enum': ACTIONS},
                'actor': {'enum': ['user', 'opp', None]},
                'move_name': _nullable_str, 'move_id': _nullable_str,
                'shield_used': {'type': 'boolean'}, 'opp_shield_used': {'type': 'boolean'},
                'user_shields': {'type': ['integer', 'null'], 'minimum': 0, 'maximum': 2},
                'opp_shields': {'type': ['integer', 'null'], 'minimum': 0, 'maximum': 2},
                'user_remaining': {'type': ['integer', 'null'], 'minimum': 0, 'maximum': 3},
                'opp_remaining': {'type': ['integer', 'null'], 'minimum': 0, 'maximum': 3},
                'switch_clock_s': _nullable_num,
                'hud_visible': {'type': 'boolean'},
                'events': {'type': 'array', 'items': {'enum': ACTIONS}},
            },
        }},
    },
}


class NoBattleFound(ValueError):
    """The video never shows the battle HUD (wrong video, or the recording starts after the battle)."""


def _median(values):
    """The middle value; with an even count the lower one, so a real drop shows from its first reading."""
    s = sorted(values)
    return s[(len(s) - 1) // 2]


def energy_from_buttons(fills: Sequence[float | None], costs: Sequence[int | None]) -> int | None:
    """
    Energy from the buttons: a part-filled button shows energy / its move's cost, so energy = fill x cost (the
    buttons agree; their median is taken). Full buttons only say energy >= cost, so with none part-filled it's the
    dearest full move's cost. None when no button was readable.
    """
    partial = [f * c for f, c in zip(fills, costs) if f is not None and c and f < 0.98]
    if partial:
        return int(round(_median(partial)))
    full = [c for f, c in zip(fills, costs) if f is not None and c and f >= 0.98]
    if full:
        return min(100, max(full))
    return None


def _battle_range(readings: Sequence[TurnReading]) -> tuple[int, int]:
    """From the first HUD reading to the end of the battle: the last HUD reading plus a few turns for the last faint."""
    hud = [i for i, r in enumerate(readings) if r.user and r.opp]
    if not hud:
        raise NoBattleFound('No battle HUD found in this video. Upload a screen recording of a GO Battle League battle.')
    return hud[0], min(len(readings), hud[-1] + 6)


def _smooth_counts(readings: list[TurnReading]) -> None:
    """
    Poké Balls and shields: median of each reading and two either side, then never rising. The median stays within
    one stretch of HUD readings: counts really change across a cut-scene (a shield used, a faint), so neighbours on
    the far side of a gap mustn't pull the change early.
    """
    for side in ('user', 'opp'):
        hud = [r for r in readings if getattr(r, side)]
        stretches: list[list[TurnReading]] = []
        for r in hud:
            if not stretches or r.turn - stretches[-1][-1].turn > 2:
                stretches.append([])
            stretches[-1].append(r)
        for key in ('balls', 'shields'):
            lowest = None
            for stretch in stretches:
                # a frame showing no balls had its icons hidden by an effect; it doesn't vote
                raw = [getattr(getattr(r, side), key) if getattr(r, side).balls else None for r in stretch]
                for k, r in enumerate(stretch):
                    near = [v for v in raw[max(0, k - 2): k + 3] if v is not None]
                    value = _median(near) if near else lowest
                    if value is not None and lowest is not None:
                        value = min(value, lowest)
                    setattr(getattr(r, side), key, value)
                    lowest = value


def _place_lone_bars(readings: list[TurnReading], active: dict[str, list], throws: list[dict]) -> None:
    """
    A lone HP bar goes to the side whose Pokémon's last HP it's closest to without being higher (HP never goes back
    up; a Pokémon not seen yet is at full). An empty bar only fits a side that loses a Poké Ball soon after, or the
    target of a charged move thrown just before (the battle's last faint has no Poké Ball to lose).
    """
    last: dict[tuple[str, str], float] = {}
    for i, r in enumerate(readings):
        if r.lone_hp is not None and not (r.user and r.user.hp is not None) and not (r.opp and r.opp.hp is not None):
            fits = []
            for side in ('user', 'opp'):
                prev = last.get((side, active[side][i]), 1.0)
                if r.lone_hp > prev + 0.05:
                    continue
                if r.lone_hp <= 0.02:
                    attacker = 'opp' if side == 'user' else 'user'
                    hit = any(t['actor'] == attacker and 0 <= i - t['index'] <= 12 for t in throws)
                    now = next((getattr(q, side).balls for q in readings[max(0, i - 6): i + 1][::-1]
                                if getattr(q, side) and getattr(q, side).balls), None)
                    later = [getattr(q, side).balls for q in readings[i + 1: i + 9] if getattr(q, side) and getattr(q, side).balls]
                    if not (hit or (now and later and min(later) < now)):
                        continue
                fits.append((abs(prev - r.lone_hp), side))
            if fits:
                side = min(fits)[1]
                if getattr(r, side) is None:
                    setattr(r, side, SideReading(species=active[side][i]))
                getattr(r, side).hp = r.lone_hp
        for side in ('user', 'opp'):
            s = getattr(r, side)
            if s and s.hp is not None:
                last[(side, active[side][i])] = s.hp


CONTRADICTED = 0.1  # HP never rises: a read this far under the next few reads of the same Pokémon was a misread


def _drop_contradicted(idx: list[int], hp: list[float]) -> list[int]:
    """
    A Pokémon's reading turns without the reads that every one of the next few clearly beats: the bar filling up as
    it comes in, or a frame caught mid-animation. Any later read near a low one confirms it, so stray high misreads
    (another bar read as this one) can't overrule a real drop.
    """
    keep = []
    for j, i in enumerate(idx):
        after = hp[j + 1: j + 4]
        if len(after) < 2 or min(after) <= hp[j] + CONTRADICTED:
            keep.append(i)
    return keep


def _smooth_hp(readings: list[TurnReading], active: dict[str, list]) -> dict[str, list[float | None]]:
    """
    Each Pokémon's HP: median over neighbouring readings within a stretch (a charged move lands while the HUD is
    hidden, so HP really jumps across a gap of 5+ turns), and never above its lowest so far. Reads that the following
    reads beat are left out (the bar filling up on entry, a frame caught mid-animation). Returns the cleaned
    readings per side (None where that turn had no reading).
    """
    out = {}
    for side in ('user', 'opp'):
        clean: list[float | None] = [None] * len(readings)
        by_species: dict[str, list[int]] = {}
        for i, r in enumerate(readings):
            s = getattr(r, side)
            if s and s.hp is not None and active[side][i]:
                by_species.setdefault(active[side][i], []).append(i)
        for idx in by_species.values():
            idx = _drop_contradicted(idx, [getattr(readings[i], side).hp for i in idx])
            if not idx:
                continue
            stretches = [[idx[0]]]
            for prev, i in zip(idx, idx[1:]):
                if i - prev > 4:
                    stretches.append([])
                stretches[-1].append(i)
            lowest = 1.0
            for stretch in stretches:
                raw = [getattr(readings[i], side).hp for i in stretch]
                for j, i in enumerate(stretch):
                    lowest = min(_median(raw[max(0, j - 2): j + 3]), lowest)
                    clean[i] = lowest
        out[side] = clean
    return out


def _move_costs(r: TurnReading, species: str | None) -> list[int | None]:
    """The buttons' move costs: from their labels when read, else the species' usual charged moves."""
    ids = list(r.charge_moves) if r.charge_moves else []
    if not any(ids) and species:
        ids = gamedata.moveset_for(species)[1:3]
    moves = gamedata.moves()
    return [moves[m].energy if m in moves else None for m in ids]


def _gaps(readings: Sequence[TurnReading]) -> list[tuple[int, int]]:
    """Index ranges [start, end) where the HUD panels are hidden (cut-scenes, prompts, switch animations)."""
    gaps, start = [], None
    for i, r in enumerate(readings):
        hidden = not (r.user and r.opp)
        if hidden and start is None:
            start = i
        elif not hidden and start is not None:
            gaps.append((start, i))
            start = None
    if start is not None:
        gaps.append((start, len(readings)))
    return gaps


def _runs(indices: list[int], max_gap: int = 4) -> list[list[int]]:
    runs: list[list[int]] = []
    for i in indices:
        if runs and i - runs[-1][-1] <= max_gap:
            runs[-1].append(i)
        else:
            runs.append([i])
    return runs


def _charged_moves(readings, active, shields, energy) -> list[dict]:
    """
    Charged moves thrown, as dicts (index, actor, move_id, shielded), from what the game announces:
    - the opponent's: "Attack incoming!" and the shield prompt (one run of them is one throw, logged where it starts);
      the move's name is the "Opponent used Y!" banner that follows within ~8 s;
    - yours (and the opponent's when you have no shields left, so no prompt): a run of the same "X used Y!" banner,
      logged where it starts, the side being whoever X is out on;
    - yours with no banner read: your energy falling by 30+ across a cut-scene of 4+ turns.
    A throw was shielded if the defender's shield count drops before the next throw (the icon updates a few seconds
    after the HUD comes back, so the whole stretch to the next throw is looked at).
    """
    n = len(readings)
    cues = _runs([i for i, r in enumerate(readings) if isinstance(r.banner, AttackIncoming) or r.shield_prompt])
    banner_runs: list[tuple[int, MoveBanner]] = []
    for run in _runs([i for i, r in enumerate(readings) if isinstance(r.banner, MoveBanner)]):
        # split a run where the banner changes (two moves back to back)
        start = run[0]
        for a, b in zip(run, run[1:]):
            if readings[b].banner != readings[a].banner:
                banner_runs.append((start, readings[start].banner))
                start = b
        banner_runs.append((start, readings[start].banner))
    used = set()
    throws = []
    for run in cues:
        a, b = run[0], run[-1]
        move_id = None
        for k, (at, banner) in enumerate(banner_runs):
            if k not in used and a < at <= b + 16 and banner.species in (active['opp'][at], None):
                move_id, _ = banner.move_id, used.add(k)
                break
        throws.append({'index': a, 'actor': 'opp', 'move_id': move_id})
    for k, (at, banner) in enumerate(banner_runs):
        if k in used:
            continue
        if banner.species == active['user'][at]:
            throws.append({'index': at, 'actor': 'user', 'move_id': banner.move_id})
        elif banner.species == active['opp'][at]:
            throws.append({'index': at, 'actor': 'opp', 'move_id': banner.move_id})
    for start, end in _gaps(readings):
        if end - start < 4 or any(start - 2 <= t['index'] < end + 2 for t in throws):
            continue
        before = next((energy[i] for i in range(start - 1, max(-1, start - 5), -1) if energy[i] is not None), None)
        after = next((energy[i] for i in range(end, min(n, end + 6)) if energy[i] is not None), None)
        if before is not None and after is not None and before - after >= 30:
            throws.append({'index': start, 'actor': 'user', 'move_id': None})
    throws.sort(key=lambda t: t['index'])
    for k, t in enumerate(throws):
        defender = 'user' if t['actor'] == 'opp' else 'opp'
        until = next((u['index'] for u in throws[k + 1:] if u['actor'] == t['actor']), n)
        before = next((shields[defender][i] for i in range(t['index'], -1, -1) if shields[defender][i] is not None), None)
        after = [shields[defender][i] for i in range(t['index'] + 1, until) if shields[defender][i] is not None]
        t['shielded'] = before is not None and bool(after) and min(after) < before
    return throws


def _carried(values, species=None, limit=None):
    """Each value carried forward to the turns that couldn't read it (not past a change of species, or `limit` turns)."""
    out, last, last_i = [], None, None
    for i, v in enumerate(values):
        if v is not None:
            last, last_i = v, i
        elif species is not None and last_i is not None and species[i] != species[last_i]:
            last = None
        elif limit is not None and last_i is not None and i - last_i > limit:
            last = None
        out.append(v if v is not None else last)
    return out


def build_turn_log(readings: Sequence[TurnReading], match_id: str) -> dict:
    lo, hi = _battle_range(readings)
    rs = copy.deepcopy(list(readings[lo:hi]))  # cleaning edits readings; the caller's stay as read
    _smooth_counts(rs)
    active = {side: _active_species(rs, side) for side in ('user', 'opp')}
    shields = {side: _carried([getattr(r, side).shields if getattr(r, side) else None for r in rs]) for side in ('user', 'opp')}
    raw_energy = [energy_from_buttons(r.energy_fill, _move_costs(r, active['user'][i])) if r.energy_fill else None
                  for i, r in enumerate(rs)]
    throws = _charged_moves(rs, active, shields, raw_energy)
    _place_lone_bars(rs, active, throws)
    hp = _smooth_hp(rs, active)

    hp_log = {}
    for side in ('user', 'opp'):
        by_species: dict[str, float] = {}
        col = []
        for i in range(len(rs)):
            sp = active[side][i]
            if hp[side][i] is not None:
                by_species[sp] = hp[side][i]
            col.append(by_species.get(sp))
        hp_log[side] = col
    balls = {side: _carried([getattr(r, side).balls if getattr(r, side) else None for r in rs]) for side in ('user', 'opp')}
    energy = _carried(raw_energy, species=active['user'], limit=3)

    actions: list[list[tuple[str, str | None, str | None]]] = [[] for _ in rs]  # (action, actor, move_id)
    shield_used = [False] * len(rs)
    opp_shield_used = [False] * len(rs)
    index_of = {r.turn: i for i, r in enumerate(rs)}
    kinds = {('user', 'switch'): 'SWITCH', ('opp', 'switch'): 'OPP_SWITCH', ('user', 'faint'): 'FAINT',
             ('opp', 'faint'): 'OPP_FAINT', ('user', 'switch_in'): 'FORCED_SWITCH', ('opp', 'switch_in'): 'OPP_FORCED_SWITCH'}
    user_switches = []
    for e in detect_events(rs):
        i = index_of[e.turn]
        actions[i].append((kinds[(e.side, e.kind)], e.side, None))
        if e.side == 'user' and e.kind == 'switch':
            user_switches.append(i)
    for t in throws:
        actions[t['index']].append(('CHARGE_MOVE_THROWN' if t['actor'] == 'user' else 'OPP_CHARGE_MOVE_THROWN',
                                    t['actor'], t['move_id']))
        if t['shielded']:
            (shield_used if t['actor'] == 'opp' else opp_shield_used)[t['index']] = True
    # fast-move hits: HP falling between two readings of the same Pokémon with no cut-scene between
    for side, action in (('user', 'OPP_FAST_MOVE_HIT'), ('opp', 'FAST_MOVE_HIT')):
        prev = None
        for i in range(len(rs)):
            if hp[side][i] is None:
                continue
            if prev is not None and active[side][prev] == active[side][i] and i - prev <= 2 \
                    and hp[side][prev] - hp[side][i] >= 0.005:
                actions[i].append((action, 'opp' if side == 'user' else 'user', None))
            prev = i

    moves = gamedata.moves()
    log = []
    for i, r in enumerate(rs):
        since = [i - s for s in user_switches if s <= i]
        clock = max(0.0, SWITCH_TIMER_S - min(since) * TURN_MS / 1000) if since else 0.0
        ordered = sorted(actions[i], key=lambda a: ACTIONS.index(a[0]))
        action, actor, move_id = ordered[0] if ordered else ('NONE', None, None)
        log.append({
            'turn': r.turn,
            'timestamp_ms': r.timestamp_ms,
            'user_active': active['user'][i],
            'opp_active': active['opp'][i],
            'user_hp_pct': None if hp_log['user'][i] is None else round(100 * hp_log['user'][i], 1),
            'opp_hp_pct': None if hp_log['opp'][i] is None else round(100 * hp_log['opp'][i], 1),
            'user_energy': energy[i],
            # the opponent's energy is never on screen; Step 2 estimates it from their fast-move hits
            'opp_energy': None,
            'action_detected': action,
            'actor': actor,
            'move_name': moves[move_id].name if move_id in moves else None,
            'move_id': move_id,
            'shield_used': shield_used[i],
            'opp_shield_used': opp_shield_used[i],
            'user_shields': shields['user'][i],
            'opp_shields': shields['opp'][i],
            'user_remaining': balls['user'][i],
            'opp_remaining': balls['opp'][i],
            'switch_clock_s': round(clock, 1),
            'hud_visible': bool(r.user and r.opp),
            'events': [a[0] for a in ordered],
        })
    teams = []
    for side in ('user', 'opp'):
        team = []
        for sp in active[side]:
            if sp and sp not in team and len(team) < 3:
                team.append(sp)
        teams.append(team)
    # a Pokémon's CP is fixed: the most common reading for it
    cps = {}
    for side in ('user', 'opp'):
        for i, r in enumerate(rs):
            s = getattr(r, side)
            if s and s.cp and active[side][i]:
                cps.setdefault((side, active[side][i]), Counter())[s.cp] += 1
    return {
        'match_id': match_id,
        'user_team': teams[0],
        'opponent_team': teams[1],
        'cp': {f'{side}:{sp}': c.most_common(1)[0][0] for (side, sp), c in cps.items()},
        'turn_log': log,
    }

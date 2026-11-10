"""Builders for hand-made turn_logs in the analysis tests (synthetic battles, not recordings)."""


def entry(turn, user='Medicham', opp='Lickitung', **fields):
    e = {
        'turn': turn, 'timestamp_ms': turn * 500, 'user_active': user, 'opp_active': opp,
        'user_hp_pct': 100.0, 'opp_hp_pct': 100.0, 'user_energy': None, 'opp_energy': None,
        'action_detected': 'NONE', 'actor': None, 'move_name': None, 'move_id': None,
        'shield_used': False, 'opp_shield_used': False, 'user_shields': 2, 'opp_shields': 2,
        'user_remaining': 3, 'opp_remaining': 3, 'switch_clock_s': 0.0, 'hud_visible': True, 'events': [],
    }
    e.update(fields)
    if e['action_detected'] != 'NONE' and not e['events']:
        e['events'] = [e['action_detected']]
    return e


def log_of(entries, user_team=('Medicham', 'Cradily', 'Swampert'), opp_team=('Lickitung', 'Azumarill', 'Charizard'),
           cp=1500):
    return {'match_id': 'test', 'user_team': list(user_team), 'opponent_team': list(opp_team),
            'cp': {f'user:{s}': cp for s in user_team} | {f'opp:{s}': cp for s in opp_team}, 'turn_log': entries}


def hits(turns):
    """Opponent fast-move hits (your HP dropping) at these turns."""
    return {t: {'action_detected': 'OPP_FAST_MOVE_HIT', 'actor': 'opp'} for t in turns}


def battle(n, user='Medicham', opp='Lickitung', at=None, **common):
    """n turns of one matchup; `at` maps a turn to the fields that differ on it."""
    at = at or {}
    return [entry(t, user, opp, **(common | at.get(t, {}))) for t in range(n)]


def user_throw(move_id='ICE_PUNCH', move_name='Ice Punch', **extra):
    return {'action_detected': 'CHARGE_MOVE_THROWN', 'actor': 'user', 'move_id': move_id, 'move_name': move_name} | extra


def opp_throw(move_id='BODY_SLAM', move_name='Body Slam', **extra):
    return {'action_detected': 'OPP_CHARGE_MOVE_THROWN', 'actor': 'opp', 'move_id': move_id, 'move_name': move_name} | extra

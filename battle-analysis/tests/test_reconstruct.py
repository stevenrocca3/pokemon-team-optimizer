"""Issue #96: the match state reconstructor (turn_log JSON)."""
from pathlib import Path

import jsonschema
import pytest

from battle_analysis.cv.detection import AttackIncoming, MoveBanner, SwitchInBanner
from battle_analysis.cv.model import SideReading, TurnReading
from battle_analysis.cv.reconstruct import TURN_LOG_SCHEMA, build_turn_log, energy_from_buttons


def reading(t, user='Medicham', opp='Lickitung', user_hp=None, opp_hp=None, lone=None, user_shields=2, opp_shields=2,
            user_balls=3, opp_balls=3, hud=True, **extra):
    """A TurnReading as the frame readers would make it (hud=False: a cut-scene, no panels or bars)."""
    if not hud:
        return TurnReading(turn=t, timestamp_ms=t * 500, **extra)
    r = TurnReading(turn=t, timestamp_ms=t * 500,
                    user=SideReading(user, 1500, user_hp, user_balls, user_shields),
                    opp=SideReading(opp, 1497, opp_hp, opp_balls, opp_shields), **extra)
    if lone is not None:
        r.lone_hp = lone
    return r


def entries(log):
    return {e['turn']: e for e in log['turn_log']}


# HP only goes down while the same Pokémon stays in (no healing moves in GBL): a misread 0.84 between 0.80 and 0.70
# is pulled back to 0.80, and the logged percentages never rise.
def test_hp_never_rises_for_the_same_pokemon():
    log = build_turn_log([reading(0, user_hp=0.8), reading(1, user_hp=0.84), reading(2, user_hp=0.8),
                          reading(3, user_hp=0.7), reading(4, user_hp=0.7)], 'm1')
    hp = [e['user_hp_pct'] for e in log['turn_log']]
    assert hp == [80.0, 80.0, 80.0, 70.0, 70.0]


# A different Pokémon coming in brings its own HP: Cradily at 100% after Medicham at 40% is not smoothed down.
def test_hp_resets_with_a_new_pokemon():
    rs = [reading(t, user_hp=0.4) for t in range(3)] + [reading(t, user='Cradily', user_hp=1.0) for t in range(3, 6)]
    hp = [e['user_hp_pct'] for e in build_turn_log(rs, 'm1')['turn_log']]
    assert hp[:3] == [40.0] * 3 and hp[3:] == [100.0] * 3


# When a Pokémon first comes in its HP bar fills up from empty over a second or so. Reads caught mid-fill (35%, then
# 70%) aren't its HP: they're dropped (None), and the full 99% that follows isn't dragged down to 35% by the
# never-rises rule.
def test_hp_bar_filling_up_on_entry_is_not_read_as_damage():
    rs = [reading(0, user_hp=0.3, opp_hp=0.35), reading(1, user_hp=0.7, opp_hp=0.7)]
    rs += [reading(t, user_hp=1.0, opp_hp=0.99) for t in range(2, 10)]
    log = build_turn_log(rs, 'm1')
    assert [e['opp_hp_pct'] for e in log['turn_log']] == [None, None] + [99.0] * 8
    assert [e['user_hp_pct'] for e in log['turn_log']] == [None, None] + [100.0] * 8


# HP never rises, so a read that the following reads of the same Pokémon clearly beat was a misread: a lone 35% between
# a full bar and a run of 99%, 96% (the pattern the container produced on rec 1) is dropped rather than held as the
# Pokémon's HP for the rest of its time in.
def test_isolated_low_misread_is_dropped():
    rs = [reading(0, lone=0.99), reading(1, lone=0.35), reading(2, hud=False), reading(3, hud=False),
          reading(4, hud=False), reading(5, hud=False)]
    rs += [reading(t, user_hp=1.0, opp_hp=0.99 if t < 9 else 0.96) for t in range(6, 12)]
    e = entries(build_turn_log(rs, 'm1'))
    assert e[1]['opp_hp_pct'] != 35.0 and e[1]['user_hp_pct'] != 35.0
    assert [e[t]['opp_hp_pct'] for t in range(6, 12)] == [99.0, 99.0, 99.0, 96.0, 96.0, 96.0]


# With only one bar on screen, it goes to the side whose last HP it's closest to without being higher: 0.55 after
# you were at 0.6 and the opponent at 0.9 is yours.
def test_lone_hp_bar_goes_to_the_side_it_fits():
    rs = [reading(0, user_hp=0.6, opp_hp=0.9), reading(1, lone=0.55), reading(2, user_hp=0.55, opp_hp=0.9)]
    e = entries(build_turn_log(rs, 'm1'))
    assert e[1]['user_hp_pct'] == 55.0 and e[1]['opp_hp_pct'] == 90.0


# Energy is read off the buttons: a button 40% full for a 45-energy move (Dynamic Punch is 50, Ice Punch 40...)
# means 18 energy. Both full means at least the dearer move's cost. Unreadable buttons give None.
def test_energy_from_button_fill_and_move_cost():
    assert energy_from_buttons([0.4, 0.36], [45, 50]) == 18
    assert energy_from_buttons([1.0, 1.0], [45, 50]) == 50
    assert energy_from_buttons([1.0, 0.8], [40, 55]) == 44
    assert energy_from_buttons([None, None], [45, 50]) is None


# Your charged move: on the real recordings "Medicham used Ice Punch!" comes up with the HUD still showing, at the
# throw, then the HUD goes away for the cut-scene. A run of that banner (turns 3-4) is one throw, logged at its first
# turn (3) as CHARGE_MOVE_THROWN with the move's name.
def test_user_charged_move_is_logged_where_its_banner_starts():
    ice_punch = MoveBanner('Medicham', 'Ice Punch', 'ICE_PUNCH')
    rs = [reading(0), reading(1), reading(2), reading(3, banner=ice_punch), reading(4, banner=ice_punch),
          reading(5, hud=False), reading(6, hud=False), reading(7), reading(8)]
    log = build_turn_log(rs, 'm1')
    e = entries(log)
    assert e[3]['action_detected'] == 'CHARGE_MOVE_THROWN'
    assert e[3]['move_name'] == 'Ice Punch'
    assert e[3]['actor'] == 'user'
    assert sum(x['action_detected'] == 'CHARGE_MOVE_THROWN' for x in log['turn_log']) == 1


# The opponent's charged move: "Attack incoming!", the shield prompt, then your shield count drops 2 -> 1: the throw is
# OPP_CHARGE_MOVE_THROWN at the "Attack incoming!" turn, and shield_used is true there (you shielded).
def test_opponent_throw_and_your_shield():
    rs = [reading(0), reading(1), reading(2, hud=False, banner=AttackIncoming()),
          reading(3, hud=False, shield_prompt=True), reading(4, hud=False, shield_prompt=True),
          reading(5, hud=False, banner=MoveBanner('Lickitung', 'Body Slam', 'BODY_SLAM')),
          reading(6, user_shields=1), reading(7, user_shields=1)]
    e = entries(build_turn_log(rs, 'm1'))
    assert e[2]['action_detected'] == 'OPP_CHARGE_MOVE_THROWN'
    assert e[2]['move_name'] == 'Body Slam'
    assert e[2]['shield_used'] is True
    assert e[6]['user_shields'] == 1


# Declining the shield (shield count stays 2) leaves shield_used false on the opponent's throw.
def test_declined_shield_is_not_shield_used():
    rs = [reading(0), reading(1, hud=False, banner=AttackIncoming()), reading(2, hud=False, shield_prompt=True),
          reading(3, hud=False, banner=MoveBanner('Lickitung', 'Body Slam', 'BODY_SLAM')), reading(4), reading(5)]
    e = entries(build_turn_log(rs, 'm1'))
    assert e[1]['action_detected'] == 'OPP_CHARGE_MOVE_THROWN'
    assert e[1]['shield_used'] is False


# Switching starts the 50 s switch timer: you switch at turn 10, so at turn 20 (5 s later) 45 s are left, and 100+
# turns later it's ready (0). Before any switch it's 0.
def test_switch_clock_counts_down_from_your_switch():
    rs = [reading(t) for t in range(10)] + [reading(t, user='Cradily') for t in range(10, 130)]
    e = entries(build_turn_log(rs, 'm1'))
    assert e[5]['switch_clock_s'] == 0
    assert e[10]['action_detected'] == 'SWITCH'
    assert e[20]['switch_clock_s'] == 45.0
    assert e[125]['switch_clock_s'] == 0


# The output matches the spec's schema: match_id, both teams, and per turn the spec's fields with the right types.
def test_turn_log_matches_the_schema():
    rs = [reading(0, user_hp=1.0, opp_hp=1.0), reading(1, user_hp=0.9, opp_hp=1.0, banner=SwitchInBanner('Medicham'))]
    log = build_turn_log(rs, 'abc123')
    jsonschema.validate(log, TURN_LOG_SCHEMA)
    assert log['match_id'] == 'abc123'
    assert log['user_team'] == ['Medicham'] and log['opponent_team'] == ['Lickitung']
    for key in ('turn', 'timestamp_ms', 'user_active', 'opp_active', 'user_hp_pct', 'opp_hp_pct', 'user_energy',
                'opp_energy', 'action_detected', 'move_name', 'shield_used'):
        assert key in log['turn_log'][0]


RECORDINGS = Path(__file__).resolve().parents[2] / 'pokemon-optimizer-backend/src/main/python/Screenrecordings'
REC1 = RECORDINGS / 'ScreenRecording_11-10-2026 00-21-39_1.MP4'


# End to end on Steven's real recording (gitignored, so skipped elsewhere). The answer key is what he reported and
# the prototype reader matched: his Swampert and Cradily vs Weezing, Togekiss, Charizard; Togekiss and Charizard
# faint, then Weezing at the end (he won); he switches Swampert -> Cradily near 1:58 and back near 2:57; four shield
# prompts, shielding only the last.
@pytest.mark.recordings
@pytest.mark.skipif(not REC1.exists(), reason='real recordings are not on this machine')
def test_real_recording_reconstructs_the_known_battle():
    from battle_analysis.cv.pipeline import analyze_video
    log = analyze_video(REC1, 'rec1')
    jsonschema.validate(log, TURN_LOG_SCHEMA)
    assert log['user_team'][:2] == ['Swampert', 'Cradily']
    assert log['opponent_team'] == ['Weezing', 'Togekiss', 'Charizard']
    faints = [(e['turn'], e['opp_active']) for e in log['turn_log'] if e['action_detected'] == 'OPP_FAINT']
    assert [sp for _, sp in faints] == ['Togekiss', 'Charizard', 'Weezing']
    switches = [e['timestamp_ms'] / 1000 for e in log['turn_log'] if e['action_detected'] == 'SWITCH']
    assert len(switches) == 2
    assert abs(switches[0] - 118) <= 3 and abs(switches[1] - 177) <= 3
    opp_throws = [e for e in log['turn_log'] if e['action_detected'] == 'OPP_CHARGE_MOVE_THROWN']
    assert len(opp_throws) == 4
    assert sum(e['shield_used'] for e in opp_throws) == 1

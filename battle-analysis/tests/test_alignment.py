"""Issue #97: fast move alignment and charged move timing."""
import json
from pathlib import Path

from battle_analysis.analysis.alignment import audit_alignment, infer_fast_move
from battle_analysis.analysis.context import resolve_form
from tests.analysis_logs import battle, hits, log_of, user_throw


# The HUD only says "Weezing"; Galarian Weezing is the one that can throw Sludge and Overheat, so those moves make it
# Galarian. With no charged moves seen, the plain species is assumed.
def test_form_is_resolved_from_the_charged_moves_seen():
    assert resolve_form('Weezing', {'SLUDGE', 'OVERHEAT'}) == 'Weezing (Galarian)'
    assert resolve_form('Weezing', {'SLUDGE_BOMB'}) == 'Weezing'
    assert resolve_form('Weezing', set()) == 'Weezing'


# Swampert knows Mud Shot (2 turns) and Water Gun (1 turn). Your HP dropping every 2 turns means Mud Shot; every turn
# means Water Gun; no hits seen means its usual fast move (Mud Shot in the rankings).
def test_opponent_fast_move_is_inferred_from_the_spacing_of_its_hits():
    assert infer_fast_move('Swampert', [10, 12, 14, 16]).id == 'MUD_SHOT'
    assert infer_fast_move('Swampert', [10, 11, 12, 13]).id == 'WATER_GUN'
    assert infer_fast_move('Swampert', []).id == 'MUD_SHOT'


# Hits sampled at 2 Hz can miss one: gaps of 2, 4, 2, 2 still read as a 2-turn move (the most common gap).
def test_a_missed_hit_does_not_change_the_inferred_move():
    assert infer_fast_move('Swampert', [10, 12, 16, 18, 20]).id == 'MUD_SHOT'


# The HP bar drains over about a second, so one hit shows as drops on 2 readings in a row (seen on the real
# recordings). Charizard hit at 10-11, 13-14, 16-17 is a 3-turn move (Air Slash / Fire Spin), not 1-turn Dragon Breath.
# Only a long unbroken run of drops (4+ turns) means a real 1-turn move.
def test_a_draining_hp_bar_counts_as_one_hit():
    assert infer_fast_move('Charizard', [10, 11, 13, 14, 16, 17]).turns == 3
    assert infer_fast_move('Charizard', list(range(10, 20))).id == 'DRAGON_BREATH'


# Opposing Swampert's Mud Shot (2 turns) last registered on turn 14. Throwing on turn 16 is turn 2 of the next Mud
# Shot: on alignment, so it lands anyway: a timing error giving 1 free Mud Shot (2 turns).
def test_throw_on_alignment_gives_a_free_fast_move():
    log = log_of(battle(20, user='Medicham', opp='Swampert', at=hits([10, 12, 14]) | {16: user_throw()}),
                 user_team=('Medicham',), opp_team=('Swampert',))
    report = audit_alignment(log)
    [t] = report['throws']
    assert (t['turn'], t['verdict'], t['opp_fast_move'], t['move_turns'], t['turn_in_cycle']) == (16, 'TIMING_ERROR', 'Mud Shot', 2, 2)
    assert t['free_fast_moves'] == 1 and t['turns_granted'] == 2
    assert t['message'] == 'Turn 16: Threw Charge Move on 2-turn alignment (+1 free Mud Shot given to opponent)'
    assert report['timing_errors'] == 1 and report['free_turns_given'] == 2


# The same throw one turn earlier (turn 15, turn 1 of the Mud Shot) is off alignment: optimal, nothing given away.
def test_throw_off_alignment_is_optimal():
    log = log_of(battle(20, user='Medicham', opp='Swampert', at=hits([10, 12, 14]) | {15: user_throw()}),
                 user_team=('Medicham',), opp_team=('Swampert',))
    report = audit_alignment(log)
    assert report['throws'][0]['verdict'] == 'OPTIMAL'
    assert report['timing_errors'] == 0 and report['optimal_throws'] == 1


# With no hits seen, turns count from the start of the matchup (turn 20 is turn 1). Against Azumarill's Bubble
# (3 turns): turn 4 or 7 of the matchup is optimal, turn 6 is on alignment (the spec's examples).
def test_turns_count_from_the_matchup_start_without_hits():
    def verdict(throw_turn):
        entries = battle(20, user='Medicham', opp='Lickitung') + [
            e | {'turn': e['turn'] + 20, 'timestamp_ms': (e['turn'] + 20) * 500}
            for e in battle(15, user='Medicham', opp='Azumarill', at={throw_turn - 20: user_throw()})]
        return audit_alignment(log_of(entries))['throws'][0]
    assert verdict(23)['verdict'] == 'OPTIMAL' and verdict(23)['turn_in_cycle'] == 4
    assert verdict(26)['verdict'] == 'OPTIMAL' and verdict(26)['turn_in_cycle'] == 7
    assert verdict(25)['verdict'] == 'TIMING_ERROR' and verdict(25)['turn_in_cycle'] == 6


# A verdict counted from a hit seen within the last two cycles is high confidence; one counted from the matchup start
# with no hits seen (missed readings could shift it) is low confidence, so the dashboard can say so.
def test_confidence_depends_on_how_the_cycle_was_counted():
    seen = log_of(battle(20, opp='Swampert', at=hits([10, 12, 14]) | {15: user_throw()}))
    unseen = log_of(battle(20, opp='Swampert', at={15: user_throw()}))
    assert audit_alignment(seen)['throws'][0]['confidence'] == 'high'
    assert audit_alignment(unseen)['throws'][0]['confidence'] == 'low'


# A 1-turn fast move (Lickitung's Lick) registers every turn, so there's no alignment to play: never an error.
def test_one_turn_moves_are_never_timing_errors():
    log = log_of(battle(20, at=hits(range(5, 15)) | {15: user_throw()}))
    assert audit_alignment(log)['throws'][0]['verdict'] == 'NO_ALIGNMENT'


# The opponent's own charged moves are not audited, only yours.
def test_only_your_throws_are_audited():
    log = log_of(battle(20, opp='Swampert', at=hits([10, 12]) | {14: {'action_detected': 'OPP_CHARGE_MOVE_THROWN', 'actor': 'opp'}}))
    assert audit_alignment(log)['throws'] == []


FIXTURES = Path(__file__).parent / 'fixtures'


# On Steven's real battle (rec 1, reconstructed) every one of his 5 charged moves gets exactly one verdict, and the
# Galarian Weezing he faced is audited against Fairy Wind (its only 2-turn move).
def test_real_battle_audits_every_throw():
    log = json.loads((FIXTURES / 'rec1_turn_log.json').read_text())
    report = audit_alignment(log)
    assert [t['turn'] for t in report['throws']] == [185, 232, 296, 349, 397]
    assert all(t['verdict'] in ('OPTIMAL', 'TIMING_ERROR', 'NO_ALIGNMENT') for t in report['throws'])
    weezing = [t for t in report['throws'] if t['opp'] == 'Weezing (Galarian)']
    assert weezing and all(t['opp_fast_move'] in ('Fairy Wind', 'Tackle') for t in weezing)

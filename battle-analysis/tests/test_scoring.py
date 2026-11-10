"""Issue #100: the Legend benchmark score, grade tiers, and summary."""
import json
from pathlib import Path

from battle_analysis.analysis.scoring import analyze_match, compute_score, tier_for
from tests.analysis_logs import battle, hits, log_of, opp_throw, user_throw

FIXTURES = Path(__file__).parent / 'fixtures'


# The spec's formula: 100 - 12 per timing error - 15 per mismanaged shield - 15 per suboptimal switch - 10 per
# suboptimal charged move choice. One of each: 100 - 12 - 15 - 15 - 10 = 48.
def test_deductions_follow_the_formula():
    assert compute_score(timing_errors=1, mismanaged_shields=1, suboptimal_switches=1, suboptimal_charges=1) == 48
    assert compute_score(timing_errors=2, mismanaged_shields=0, suboptimal_switches=1, suboptimal_charges=0) == 61


# The score never goes below 0 (9 timing errors would be -8).
def test_score_is_floored_at_zero():
    assert compute_score(timing_errors=9, mismanaged_shields=0, suboptimal_switches=0, suboptimal_charges=0) == 0


# Tier edges from the spec: 90-100 Legend, 75-89 Veteran / Expert, 50-74 Ace / Mid-Ladder, 0-49 Critical.
def test_tier_boundaries():
    assert tier_for(100)['name'] == tier_for(90)['name'] == 'Legend Tier Execution'
    assert tier_for(89)['name'] == tier_for(75)['name'] == 'Veteran / Expert Tier Execution'
    assert tier_for(74)['name'] == tier_for(50)['name'] == 'Ace / Mid-Ladder Execution'
    assert tier_for(49)['name'] == tier_for(0)['name'] == 'Critical Execution Flaws (Needs Major Rebuilding)'


# A battle with no errors scores 100, Legend, and the summary says so plainly instead of inventing criticism.
def test_clean_battle_is_legend_with_no_flaws():
    report = analyze_match(log_of(battle(20)))
    assert report['score'] == 100 and report['tier']['name'] == 'Legend Tier Execution'
    assert report['summary']['fatal_flaws'] == ['No execution errors detected.']


# A timing error (+1 free Mud Shot, 2 turns) and a wasted shield (resisted Body Slam on Registeel at 90%) cost
# 12 + 15: score 73. The fatal flaws lead with the costlier mistake (the shield), and the turn efficiency line counts
# the 2 turns given away.
def test_findings_are_ranked_by_cost_and_summarised():
    entries = battle(20, user='Medicham', opp='Swampert', at=hits([10, 12, 14]) | {16: user_throw()}) + [
        e | {'turn': e['turn'] + 20, 'timestamp_ms': (e['turn'] + 20) * 500}
        for e in battle(10, user='Registeel', opp='Lickitung', at={5: opp_throw(shield_used=True, user_hp_pct=90.0)})]
    report = analyze_match(log_of(entries, user_team=('Medicham', 'Registeel', 'Cradily'), opp_team=('Swampert', 'Lickitung')))
    assert report['score'] == 73
    assert report['deductions'] == {'timing_errors': 12, 'mismanaged_shields': 15, 'suboptimal_switches': 0, 'suboptimal_charges': 0}
    flaws = report['summary']['fatal_flaws']
    assert flaws[0].startswith('Turn 25: wasted a shield on Body Slam') and flaws[1].startswith('Turn 16: Threw Charge Move')
    assert report['summary']['turn_efficiency'] == 'Turns lost to suboptimal timing: 2 (1 free fast move given to the opponent). Off-alignment throws: 0 of 1.'
    assert report['summary']['shield_audit'] == 'Used 1 shield, wasted 1.'
    assert [f['category'] for f in report['findings']] == ['timing', 'shield']


# Steven's real battles, scored from what the audits found on them: rec 1 has one timing error (100 - 12 = 88,
# Veteran); rec 2 has two timing errors and one missed catch (100 - 24 - 15 = 61, Ace). The same log always gives the
# same report.
def test_real_battles_score_deterministically():
    rec1 = json.loads((FIXTURES / 'rec1_turn_log.json').read_text())
    rec2 = json.loads((FIXTURES / 'rec2_turn_log.json').read_text())
    assert (analyze_match(rec1)['score'], analyze_match(rec1)['tier']['name']) == (88, 'Veteran / Expert Tier Execution')
    assert (analyze_match(rec2)['score'], analyze_match(rec2)['tier']['name']) == (61, 'Ace / Mid-Ladder Execution')
    assert analyze_match(rec2) == analyze_match(rec2)

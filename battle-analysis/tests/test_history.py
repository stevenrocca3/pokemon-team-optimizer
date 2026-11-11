"""Issue #120 (analysis half): what a stored battle record needs from the report: who won, and the spec's metrics."""
import json
from pathlib import Path

from battle_analysis.analysis.history import battle_outcome
from battle_analysis.analysis.scoring import analyze_match

FIXTURES = Path(__file__).parent / 'fixtures'


def fixture(name: str) -> dict:
    return json.loads((FIXTURES / f'{name}_turn_log.json').read_text())


def ending(user_hp, user_left, opp_hp, opp_left, then_hidden=True) -> dict:
    """A battle's last moments: a few turns of HUD with the given state, then (usually) the HUD gone."""
    turn = {'hud_visible': True, 'user_hp_pct': user_hp, 'opp_hp_pct': opp_hp, 'user_remaining': user_left,
            'opp_remaining': opp_left}
    middle = dict(turn, user_hp_pct=60.0, opp_hp_pct=60.0)
    entries = [middle, middle, turn, turn]
    if then_hidden:
        entries.append(dict(turn, hud_visible=False, user_hp_pct=None, opp_hp_pct=None))
    return {'turn_log': entries}


# Rec 2 ends with the opponent's last Pokémon (Morpeko, 1 left) at 0 HP: a win.
def test_opponents_last_pokemon_fainting_is_a_win():
    assert battle_outcome(fixture('rec2')) == 'WIN'


# Your last Pokémon at 0 HP with nothing behind it is a loss.
def test_your_last_pokemon_fainting_is_a_loss():
    assert battle_outcome(ending(user_hp=0.0, user_left=1, opp_hp=35.0, opp_left=2)) == 'LOSS'


# Rec 1's recording stops mid-battle (Weezing at 51%, both sides with Pokémon left): no outcome, rather than a guess.
def test_recording_that_stops_early_has_no_outcome():
    assert battle_outcome(fixture('rec1')) is None


# A faint with Pokémon still to come isn't the end, even if the recording stops right there.
def test_a_faint_with_pokemon_behind_it_is_not_the_end():
    assert battle_outcome(ending(user_hp=50.0, user_left=3, opp_hp=0.0, opp_left=2)) is None


# Both last Pokémon at 0 (a double knock-out) is a tie in GO, which counts as neither: no outcome.
def test_double_knock_out_has_no_outcome():
    assert battle_outcome(ending(user_hp=0.0, user_left=1, opp_hp=0.0, opp_left=1)) is None


# The report carries the outcome and the spec's three history metrics. Rec 1: 2 free turns given, 3 of 4 throws
# that had an alignment were off it (75%), its one shield was justified (100%).
def test_report_carries_outcome_and_history_metrics_rec1():
    report = analyze_match(fixture('rec1'))
    assert report['outcome'] is None
    assert report['history'] == {'turns_lost': 2, 'timing_accuracy_pct': 75.0, 'shield_efficiency_pct': 100.0}


# Rec 2: a win; 4 free turns given; 4 of 6 throws off alignment (66.7%); no shields used, so no shield efficiency
# (null, not 0% or 100%).
def test_report_carries_outcome_and_history_metrics_rec2():
    report = analyze_match(fixture('rec2'))
    assert report['outcome'] == 'WIN'
    assert report['history'] == {'turns_lost': 4, 'timing_accuracy_pct': 66.7, 'shield_efficiency_pct': None}

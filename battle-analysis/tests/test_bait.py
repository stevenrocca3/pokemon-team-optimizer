"""Issue #99: charged move selection and shield baiting."""
import json
from pathlib import Path

from battle_analysis.analysis.bait import audit_move_choice
from tests.analysis_logs import battle, log_of, user_throw

TEAM = ('Swampert', 'Medicham', 'Cradily')
EQ = ('EARTHQUAKE', 'Earthquake')
HC = ('HYDRO_CANNON', 'Hydro Cannon')


def swampert_log(n=12, at=None, **common):
    return log_of(battle(n, user='Swampert', opp='Lickitung', at=at, **common), user_team=TEAM)


# Swampert throws Earthquake (65 energy) into a shield while it had Hydro Cannon (40): the shield would have gone on
# the cheaper move just the same. A nuke into a shield.
def test_nuke_into_a_shield_with_a_cheaper_move_available():
    log = swampert_log(at={5: user_throw(*EQ, opp_shield_used=True, opp_shields=2)})
    [f] = audit_move_choice(log)['findings']
    assert f['kind'] == 'NUKE_INTO_SHIELD' and f['turn'] == 5
    assert f['message'] == ('Turn 5: threw Earthquake (65 energy) into a shield. '
                            'Hydro Cannon (40 energy) would have drawn it for 25 less.')


# Baiting the shield with the cheap Hydro Cannon is the right play: nothing flagged.
def test_baiting_with_the_cheap_move_is_fine():
    log = swampert_log(at={5: user_throw(*HC, opp_shield_used=True, opp_shields=2)})
    assert audit_move_choice(log)['findings'] == []


# Endgame (your last Pokémon): Earthquake goes into a shield and Swampert faints before throwing again. The bait was
# needed to win, so this is a missed bait, not just an expensive shield.
def test_endgame_nuke_into_shield_then_fainting_is_a_missed_bait():
    at = {5: user_throw(*EQ, opp_shield_used=True, opp_shields=1), 10: {'action_detected': 'FAINT', 'actor': 'user'}}
    [f] = audit_move_choice(swampert_log(at=at, user_remaining=1))['findings']
    assert f['kind'] == 'MISSED_BAIT'


# With no shields left on their side, Hydro Cannon does 32.8% to Lickitung where Earthquake does 48.9%, and you had
# 70 energy (enough for Earthquake): the bigger move was the right one.
def test_lower_damage_move_into_no_shields_with_the_bigger_one_affordable():
    log = swampert_log(at={4: {'user_energy': 70}, 5: user_throw(*HC, opp_shields=0, user_energy=70)})
    [f] = audit_move_choice(log)['findings']
    assert f['kind'] == 'LOWER_DAMAGE_CHOICE'
    assert f['message'] == ('Turn 5: threw Hydro Cannon (32.8%) into no shields with energy for Earthquake (48.9%).')


# Not flagged: you only had 50 energy (Earthquake costs 65), or Hydro Cannon was enough to knock Lickitung out (25%).
def test_lower_damage_move_is_fine_when_unaffordable_or_already_lethal():
    poor = swampert_log(at={4: {'user_energy': 50}, 5: user_throw(*HC, opp_shields=0, user_energy=50)})
    lethal = swampert_log(at={4: {'user_energy': 70, 'opp_hp_pct': 25.0}, 5: user_throw(*HC, opp_shields=0, user_energy=70, opp_hp_pct=25.0)})
    assert audit_move_choice(poor)['findings'] == []
    assert audit_move_choice(lethal)['findings'] == []


# Steven's real battle (rec 1): his Weezing-shielded throws were Hydro Cannon (his cheap move) and Cradily's Bulldoze,
# only 5 energy dearer than Rock Tomb: neither is a nuke into a shield.
def test_real_battle_has_no_nukes_into_shields():
    log = json.loads((Path(__file__).parent / 'fixtures/rec1_turn_log.json').read_text())
    assert [f for f in audit_move_choice(log)['findings'] if f['kind'] == 'NUKE_INTO_SHIELD'] == []

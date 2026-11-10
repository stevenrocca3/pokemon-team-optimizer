"""Issue #98: shield efficiency, switch lag, and caught charged moves."""
import json
from pathlib import Path

from battle_analysis.analysis.tactics import audit_catches, audit_shields, audit_switch_lag
from tests.analysis_logs import battle, entry, log_of, opp_throw

TEAM = ('Registeel', 'Swampert', 'Cradily')


# Lickitung's Body Slam (normal) is resisted by Registeel (steel, x0.625) and does 13.2% of its HP at default IVs.
# Shielding it at 90% HP (would be left on ~77%) burns a shield on a resisted bait: wasteful.
def test_shielding_a_resisted_bait_is_wasteful():
    log = log_of(battle(10, user='Registeel', at={5: opp_throw(shield_used=True, user_hp_pct=90.0)}), user_team=TEAM)
    [s] = audit_shields(log)['shields']
    assert s['verdict'] == 'WASTEFUL'
    assert s['damage_pct'] == 13.2 and s['hp_before_pct'] == 90.0
    assert 'resisted' in s['message']


# Body Slam into Medicham does 25.5%; at full HP it would leave ~75%: it easily survives, so shielding was wasteful
# even though the move isn't resisted.
def test_shielding_a_hit_you_easily_survive_is_wasteful():
    log = log_of(battle(10, user='Medicham', at={5: opp_throw(shield_used=True, user_hp_pct=100.0)}))
    assert audit_shields(log)['shields'][0]['verdict'] == 'WASTEFUL'


# Power Whip (grass) into Swampert (water/ground, x2.56) does 97.8%: at 60% HP that's a knock-out, so the shield
# was right.
def test_shielding_a_knockout_is_justified():
    log = log_of(battle(10, user='Swampert', at={5: opp_throw('POWER_WHIP', 'Power Whip', shield_used=True, user_hp_pct=60.0)}),
                 user_team=TEAM)
    s = audit_shields(log)
    assert s['shields'][0]['verdict'] == 'JUSTIFIED'
    assert s['mismanaged'] == 0


# Steven's real shield (rec 1, 3:02): Galarian Weezing's Overheat would have done 41.9% to his Swampert on 32.7%:
# a knock-out, so it's justified, and it's the only shield he used.
def test_real_battle_shield_is_justified():
    log = json.loads((Path(__file__).parent / 'fixtures/rec1_turn_log.json').read_text())
    report = audit_shields(log)
    assert [(s['turn'], s['verdict']) for s in report['shields']] == [(364, 'JUSTIFIED')]


def lag_battle(n=20, clock=0.0, remaining=3, you_lose_hp=True):
    """
    Your Cradily stuck on Doublade: Acid can't touch steel and its grass and rock moves are resisted, so counting fast
    and charged moves Doublade puts out ~2.3x the pressure. Your HP falls 3 points a turn and theirs holds (or the
    reverse with you_lose_hp=False).
    """
    falling = {t: {'user_hp_pct' if you_lose_hp else 'opp_hp_pct': 100.0 - 3 * t} for t in range(n)}
    return log_of(battle(n, user='Cradily', opp='Doublade', switch_clock_s=clock, user_remaining=remaining, at=falling),
                  user_team=TEAM, opp_team=('Doublade',))


# Staying in a hard-counter matchup for more than 3 turns with your switch ready is switch lag: here all 20 turns,
# flagged once at turn 3 (its 4th turn, counting from turn 0), with how long you stayed.
def test_staying_in_a_hard_counter_with_switch_ready_is_lag():
    [lag] = audit_switch_lag(lag_battle())['lags']
    assert lag['turn'] == 3 and lag['turns_stayed'] == 20
    assert lag['opp'] == 'Doublade' and lag['user'] == 'Cradily'
    assert 'Doublade' in lag['message']


# Not lag: the switch timer is still running (you couldn't switch), or it's your last Pokémon (nothing to switch to).
def test_no_lag_without_a_switch_to_make():
    assert audit_switch_lag(lag_battle(clock=30.0))['lags'] == []
    assert audit_switch_lag(lag_battle(remaining=1))['lags'] == []


# On paper it's a hard counter, but the HP on screen says you were winning the exchange (their HP falling, yours
# not): the estimate is overruled by what happened, so it's not lag.
def test_no_lag_when_the_hp_says_you_were_winning():
    assert audit_switch_lag(lag_battle(you_lose_hp=False))['lags'] == []


# 3 turns isn't over the limit: you can take a moment to switch.
def test_three_turns_is_not_lag():
    assert audit_switch_lag(lag_battle(n=3))['lags'] == []


# You switch Swampert out for Cradily on turn 10 and Lickitung's Power Whip lands on Cradily on turn 12: neutral on
# Cradily (grass is resisted by its grass type but super effective on its rock type: x1.0, 28.5%) where Swampert takes
# x2.56 (97.8%). A successful catch.
def test_switching_into_a_charged_move_on_a_resister_is_a_catch():
    entries = [entry(t, user='Swampert') for t in range(10)] + \
              [entry(t, user='Cradily', **({'action_detected': 'SWITCH', 'actor': 'user'} if t == 10 else {}) |
                     (opp_throw('POWER_WHIP', 'Power Whip') if t == 12 else {})) for t in range(10, 16)]
    report = audit_catches(log_of(entries, user_team=TEAM))
    [c] = report['catches']
    assert c['verdict'] == 'CAUGHT' and c['caught_on'] == 'Cradily' and c['instead_of'] == 'Swampert'


# Power Whip lands on Swampert (x2.56) while Registeel, which resists it (steel vs grass, x0.625), sat on the bench
# with your switch ready: a missed catch. (Cradily on the bench is only neutral to it, so it isn't the answer.)
def test_taking_a_super_effective_move_with_a_resister_on_the_bench_is_a_missed_catch():
    log = log_of(battle(16, user='Swampert', at={12: opp_throw('POWER_WHIP', 'Power Whip')}), user_team=TEAM)
    [c] = audit_catches(log)['catches']
    assert c['verdict'] == 'MISSED' and c['better'] == 'Registeel'
    assert audit_catches(log)['missed'] == 1


# No missed catch when the resister had already fainted.
def test_no_missed_catch_with_a_fainted_resister():
    at = {2: {'action_detected': 'FAINT', 'actor': 'user', 'user_active': 'Registeel'}, 12: opp_throw('POWER_WHIP', 'Power Whip')}
    log = log_of(battle(16, user='Swampert', at=at), user_team=TEAM)
    assert audit_catches(log)['catches'] == []

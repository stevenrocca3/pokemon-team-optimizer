"""Issue #94: Pokémon identification and switch / faint event detection."""
import cv2
import numpy as np
import pytest

from battle_analysis.cv.detection import (
    AttackIncoming,
    MoveBanner,
    SwitchInBanner,
    detect_events,
    is_switch_flash,
    parse_banner,
    read_panels,
    species_matcher,
    teams_from,
)
from battle_analysis.cv.model import SideReading, TurnReading


@pytest.fixture(scope='module')
def match():
    return species_matcher()


# The HUD shows the species name (or a nickname). An exact name reads as that species.
def test_exact_hud_name_is_the_species(match):
    assert match('Swampert') == 'Swampert'


# OCR often glues junk to the end ("Swampert®", "Weezingl"): the longest species name the text starts with wins, so
# "Weezingl" is Weezing, and "Charizard" isn't cut short to a shorter name it happens to start with.
def test_trailing_ocr_junk_is_ignored(match):
    assert match('Swampert®') == 'Swampert'
    assert match('Weezingl') == 'Weezing'


# OCR swaps look-alike letters ("Swampcrt", "Togekiss" as "Togeklss"); a close spelling still matches.
def test_misread_letters_still_match_by_spelling(match):
    assert match('Swampcrt') == 'Swampert'
    assert match('Togeklss') == 'Togekiss'


# The battle HUD writes Megas as "Mega Altaria"; the game data calls it "Altaria (Mega)". Regional forms show the
# plain name, so "Stunfisk" is Stunfisk.
def test_mega_and_regional_form_names(match):
    assert match('Mega Altaria') == 'Altaria (Mega)'
    assert match('Stunfisk') == 'Stunfisk'


# A nickname isn't a species: no guess, so the reconstructor falls back to the bench and banners instead.
def test_nickname_is_not_guessed(match):
    assert match('Bob') is None
    assert match('') is None


# "Go, Cradily!" is shown when YOUR Pokémon comes in: it's a switch-in banner naming Cradily.
def test_go_banner_is_a_user_switch_in(match):
    assert parse_banner('Go, Cradily!', match) == SwitchInBanner('Cradily')


# "Togekiss used Ancient Power!" names who threw a charged move and which one (as the game data's move ID too),
# even with OCR's trailing "l" for "!".
def test_used_banner_names_the_thrower_and_the_charged_move(match):
    banner = parse_banner('Togekiss used Ancient Powerl', match)
    assert banner == MoveBanner('Togekiss', 'Ancient Power', 'ANCIENT_POWER')


# "Attack incoming!" comes just before the shield decision; other text ("SUPER EFFECTIVE!") is no banner.
def test_attack_incoming_and_non_banners(match):
    assert parse_banner('Attack incoming!', match) == AttackIncoming()
    assert parse_banner('SUPER EFFECTIVE!', match) is None


def battlefield(brightness=60):
    """A synthetic dark-blue battlefield frame (not a real recording)."""
    img = np.zeros((1280, 720, 3), np.uint8)
    img[:] = (brightness // 2, brightness // 2, brightness)
    return img


# The switch-in flash whites out most of the screen for a frame: a jump from a dark field to a mostly white frame.
def test_white_flash_after_dark_frame_is_a_switch_flash():
    flash = battlefield()
    flash[200:1100] = 245
    assert is_switch_flash(battlefield(), flash)


# A brighter scene (a day battle, a bright attack) without a white-out is not a flash, and nor is white staying white.
def test_bright_scene_or_steady_white_is_not_a_flash():
    assert not is_switch_flash(battlefield(60), battlefield(140))
    white = np.full((1280, 720, 3), 245, np.uint8)
    assert not is_switch_flash(white, white)


def hud_frame(left, right):
    """
    A synthetic HUD (not a real frame): the two light top panels in the real layout (each ~38% of the width and 5% of
    the height, 6% down), with the name and CP drawn on the top line and the Poké Ball / shield row below.
    """
    img = battlefield()
    font = cv2.FONT_HERSHEY_SIMPLEX
    for x0, (name, cp), cp_first in ((16, left, False), (436, right, True)):
        cv2.rectangle(img, (x0, 80), (x0 + 268, 160), (225, 228, 235), -1)
        first, second = (f'CP {cp}', name) if cp_first else (name, f'CP {cp}')
        cv2.putText(img, first, (x0 + 10, 108), font, 0.7, (50, 50, 50), 2, cv2.LINE_AA)
        cv2.putText(img, second, (x0 + (115 if cp_first else 150), 108), font, 0.7, (50, 50, 50), 2, cv2.LINE_AA)
    return img


# The top-left panel is the player's, the top-right the opponent's: each reads as its species and CP.
def test_hud_panels_read_species_and_cp_for_each_side(match):
    panels = read_panels(hud_frame(('Medicham', 1498), ('Lickitung', 1497)), match)
    assert panels is not None
    user, opp = panels
    assert (user.species, user.cp) == ('Medicham', 1498)
    assert (opp.species, opp.cp) == ('Lickitung', 1497)


# No HUD (an intro, a charged-move cut-scene) gives no panels rather than a guess.
def test_no_hud_reads_as_none(match):
    assert read_panels(battlefield(), match) is None


def turns(user_species, opp_species, user_hp=None, opp_hp=None, user_balls=None, opp_balls=None):
    """TurnReadings from per-turn lists (None = that side wasn't read that turn)."""
    out = []
    n = len(user_species)
    for t in range(n):
        out.append(TurnReading(
            turn=t, timestamp_ms=t * 500,
            user=SideReading(species=user_species[t], hp=(user_hp or [1.0] * n)[t], balls=(user_balls or [3] * n)[t]),
            opp=SideReading(species=opp_species[t], hp=(opp_hp or [1.0] * n)[t], balls=(opp_balls or [3] * n)[t]),
        ))
    return out


# You swap Swampert (still at 50%) for Cradily at turn 6, and no Poké Ball is lost: one voluntary switch, logged at
# the first turn Cradily is out.
def test_species_change_with_hp_left_is_a_voluntary_switch():
    log = turns(['Swampert'] * 6 + ['Cradily'] * 4, ['Weezing'] * 10, user_hp=[0.5] * 6 + [0.9] * 4)
    events = detect_events(log)
    assert [(e.turn, e.side, e.kind, e.previous, e.species) for e in events] == [(6, 'user', 'switch', 'Swampert', 'Cradily')]


# A single turn where OCR read the wrong name (Cradily for one frame) is not a switch there and back.
def test_one_turn_misread_is_not_a_switch():
    log = turns(['Swampert', 'Swampert', 'Cradily', 'Swampert', 'Swampert', 'Swampert'], ['Weezing'] * 6)
    assert detect_events(log) == []


# Togekiss's HP hits 0 at turn 4 and the opponent's Poké Balls go 3 -> 2: a faint at turn 4, then Charizard comes in
# as a forced switch-in (not a voluntary switch) at turn 6.
def test_hp_zero_and_lost_ball_is_a_faint_then_forced_switch_in():
    log = turns(['Cradily'] * 9, ['Togekiss'] * 6 + ['Charizard'] * 3,
                opp_hp=[0.3, 0.2, 0.1, 0.05, 0.0, 0.0, 1.0, 1.0, 1.0], opp_balls=[3] * 5 + [2] * 4)
    events = detect_events(log)
    assert [(e.turn, e.side, e.kind, e.species) for e in events] == [
        (4, 'opp', 'faint', 'Togekiss'), (6, 'opp', 'switch_in', 'Charizard')]


# Teams are each side's first three different species, in the order they came out.
def test_teams_are_the_first_three_species_seen_per_side():
    log = turns(['Swampert', 'Swampert', 'Cradily', 'Cradily', 'Swampert', 'Swampert', 'Clodsire', 'Clodsire'],
                ['Weezing', 'Weezing', 'Togekiss', 'Togekiss', 'Charizard', 'Charizard', 'Weezing', 'Weezing'])
    assert teams_from(log) == (['Swampert', 'Cradily', 'Clodsire'], ['Weezing', 'Togekiss', 'Charizard'])

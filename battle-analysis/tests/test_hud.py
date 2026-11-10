"""Issue #95: HUD metric trackers (HP, energy, Poké Balls and shields, shield prompt, switch timer)."""
import math

import cv2
import numpy as np
import pytest

from battle_analysis.cv.detection import Panel
from battle_analysis.cv.hud import (
    BUTTON_CENTRES,
    charge_buttons,
    icon_counts,
    read_hp,
    shield_bubble,
    shield_prompt,
    switch_timer,
)

W, H = 591, 1280  # the game area of a normalized iPhone recording


def field():
    """A synthetic night battlefield (not a real frame)."""
    img = np.zeros((H, W, 3), np.uint8)
    img[:] = (35, 45, 90)
    return img


def draw_hp_bar(img, y, fill, colour=(60, 200, 170)):
    """The in-scene HP bar as the game draws it: two light border lines framing the fill, then the dark track."""
    x0, x1 = int(0.35 * W), int(0.35 * W) + int(0.29 * W)
    gap = int(0.026 * W)
    img[y - gap // 2, x0:x1] = (235, 235, 235)
    img[y + gap // 2, x0:x1] = (235, 235, 235)
    inner = img[y - gap // 2 + 1: y + gap // 2, x0 + 2: x1 - 2]
    inner[:] = (30, 30, 35)
    inner[:, : int(fill * inner.shape[1])] = colour


# Two bars on screen: the upper one is the opponent's (further from the camera), the lower one yours. A bar 62% filled
# reads 0.62 and one 30% filled reads 0.30 (within 2 points).
def test_two_hp_bars_read_as_opponent_on_top_and_you_below():
    img = field()
    draw_hp_bar(img, int(0.30 * H), 0.62)
    draw_hp_bar(img, int(0.62 * H), 0.30, colour=(230, 200, 40))  # yellow: under half
    hp = read_hp(img)
    assert hp.opp == pytest.approx(0.62, abs=0.02)
    assert hp.user == pytest.approx(0.30, abs=0.02)
    assert hp.lone is None


# With one bar showing (the other side's is off screen) neither side is guessed here; it's handed on as the lone bar.
def test_a_single_bar_is_reported_as_lone():
    img = field()
    draw_hp_bar(img, int(0.40 * H), 0.85)
    hp = read_hp(img)
    assert (hp.user, hp.opp) == (None, None)
    assert hp.lone == pytest.approx(0.85, abs=0.02)


# An empty bar (fainted) reads 0, not "no bar".
def test_empty_bar_reads_zero():
    img = field()
    draw_hp_bar(img, int(0.30 * H), 0.0)
    draw_hp_bar(img, int(0.62 * H), 1.0)
    hp = read_hp(img)
    assert hp.opp == pytest.approx(0.0, abs=0.02)
    assert hp.user == pytest.approx(1.0, abs=0.02)


def draw_button(img, centre, fill, colour=(210, 160, 100), ring=True):
    """A charged move button: a light ring, a dark disc, and the move's colour filling it from the bottom."""
    cx, cy = centre
    r = int(0.076 * W)
    if ring:
        cv2.circle(img, (cx, cy), int(1.65 * r), (200, 200, 210), 3, cv2.LINE_AA)
    cv2.circle(img, (cx, cy), r, (40, 40, 60), -1)
    mask = np.zeros(img.shape[:2], np.uint8)
    cv2.circle(mask, (cx, cy), r, 1, -1)
    level = int(round(cy + r - fill * 2 * r))
    mask[:level] = 0
    img[mask.astype(bool)] = colour


def button_centres():
    return [(int(fx * W), H - int(fy * W)) for fx, fy in BUTTON_CENTRES]


# Each button fills from the bottom as energy builds: 29% and 62% fills read as such (within 4 points), left then right.
def test_charge_button_fill_is_measured_bottom_up():
    img = field()
    left, right = button_centres()
    draw_button(img, left, 0.29)
    draw_button(img, right, 0.62, colour=(190, 180, 140))
    fills = [b.fill for b in charge_buttons(img)]
    assert fills[0] == pytest.approx(0.29, abs=0.04)
    assert fills[1] == pytest.approx(0.62, abs=0.04)


# A full button (move ready) reads 1.0 and an empty one 0.0.
def test_full_and_empty_buttons():
    img = field()
    left, right = button_centres()
    draw_button(img, left, 1.0, colour=(90, 150, 230))
    draw_button(img, right, 0.0)
    assert [b.fill for b in charge_buttons(img)] == [pytest.approx(1.0, abs=0.04), pytest.approx(0.0, abs=0.04)]


# With no buttons on screen (a cut-scene, the shield prompt) there's no ring, so no fill is reported: an unreadable
# button is None, never "0 energy".
def test_no_buttons_on_screen_reads_none():
    assert [b.fill for b in charge_buttons(field())] == [None, None]


def panel_with_icons(balls, shields):
    """A synthetic top panel: name line, then a row of red Poké Balls and pink shields."""
    img = field()
    x, y, w, h = 13, 79, 226, 65
    img[y:y + h, x:x + w] = (228, 230, 236)
    cx = x + 12
    for _ in range(balls):
        cv2.circle(img, (cx, y + 48), 7, (225, 40, 45), -1)
        cx += 20
    cx += 8
    for _ in range(shields):
        cv2.circle(img, (cx, y + 48), 7, (230, 140, 230), -1)
        cx += 20
    return img, Panel('Swampert', 'Swampert', 1497, x, y, w, h)


# The panel's lower row shows a red Poké Ball per Pokémon not yet fainted and a pink shield per shield left.
@pytest.mark.parametrize('balls,shields', [(3, 2), (2, 1), (1, 0)])
def test_poke_balls_and_shields_are_counted(balls, shields):
    img, panel = panel_with_icons(balls, shields)
    assert icon_counts(img, panel) == (balls, shields)


def pink_hexagon(img, centre, radius):
    pts = [(int(centre[0] + radius * math.cos(a)), int(centre[1] + radius * math.sin(a)))
           for a in np.linspace(0, 2 * math.pi, 7)[:-1]]
    cv2.fillPoly(img, [np.array(pts)], (225, 150, 235))


# "Use a Protect Shield?" puts a big pink hexagon button at the bottom centre.
def test_shield_prompt_is_the_big_pink_button():
    img = field()
    pink_hexagon(img, (W // 2, int(0.84 * H)), int(0.12 * W))
    assert shield_prompt(img)
    assert not shield_prompt(field())


# A shield being used throws a pink bubble over the defender mid-screen (no prompt button at the bottom).
def test_shield_bubble_mid_screen():
    img = field()
    pink_hexagon(img, (W // 2, int(0.5 * H)), int(0.25 * W))
    assert shield_bubble(img)
    assert not shield_bubble(field())


def bench_icon(elapsed):
    """
    A synthetic bench icon on the right: a pale translucent card, darkened by a wedge from 12 o'clock clockwise
    covering the elapsed share of the switch timer (None: no timer running).
    """
    img = field()
    x, y, s = int(0.82 * W), int(0.60 * H), int(0.15 * W)
    img[y:y + s, x:x + s] = (170, 180, 200)
    if elapsed:
        cx, cy = x + s // 2, y + s // 2
        yy, xx = np.mgrid[y:y + s, x:x + s]
        angle = (np.degrees(np.arctan2(xx - cx, -(yy - cy))) + 360) % 360  # 0 at 12 o'clock, clockwise
        card = img[y:y + s, x:x + s]
        card[angle < 360 * elapsed] = (60, 65, 85)
    return img


# The 50 s switch timer is drawn as a wedge sweeping round the bench icons: a quarter swept is 12.5 s gone,
# 37.5 s left (within 2 s); 60% swept is 20 s left.
@pytest.mark.parametrize('elapsed,left', [(0.25, 37.5), (0.6, 20.0)])
def test_switch_timer_reads_seconds_left_from_the_wedge(elapsed, left):
    assert switch_timer(bench_icon(elapsed)) == pytest.approx(left, abs=2)


# No wedge: the switch is ready (0 s left). No bench icons at all (last Pokémon, or hidden): unknown (None).
def test_switch_timer_ready_or_unknown():
    assert switch_timer(bench_icon(None)) == 0
    assert switch_timer(field()) is None

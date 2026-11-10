"""
Numbers off the battle HUD, by pixel: HP bars, the charged move buttons' energy fill, the Poké Ball and shield icons,
the shield prompt and bubble, and the switch timer wedge on the bench icons.

Every reader takes the game area of a normalized frame (ContentBox.crop) and works in fractions of its width and
height, so it doesn't matter what phone recorded it. A reader that can't see its element returns None (unknown),
never a made-up zero: the reconstructor fills gaps from neighbouring turns.
"""
import math
from dataclasses import dataclass

import cv2
import numpy as np

from battle_analysis.cv import ocr
from battle_analysis.cv.detection import Panel, match_move

# Charged move buttons' centres as (x share of width, height above the bottom as a share of width); the bottom bar
# is laid out by width, so these hold for tall and short phones alike. Measured on real recordings.
BUTTON_CENTRES = [(0.308, 0.234), (0.689, 0.234)]
BUTTON_RADIUS = 0.076  # the inner disc, share of width; the light ring around it is ~1.65x that
SWITCH_TIMER_S = 50


# --- HP bars ----------------------------------------------------------------------------------------------------
@dataclass(frozen=True)
class HpReading:
    """Your HP and the opponent's (0..1). With only one bar on screen its side isn't known yet: that's `lone`."""
    user: float | None
    opp: float | None
    lone: float | None


def hp_bars(a: np.ndarray) -> list[tuple[int, int, int, float]]:
    """
    The in-scene HP bars as (x0, x1, y, hp from 0 to 1), top of the screen first (ported from the prototype reader,
    tools/gbl-reader-prototype/read_battle.py, which was checked against real recordings).
    A bar is framed by two light border lines, top and bottom, ~0.29 of the width long and ~0.026 apart, with the
    same ends: that pair is what's looked for, since attack effects and the white field lines never make one.
    Inside, along the middle: the fill (teal when healthy, then yellow, then red), maybe a chunk of just-lost HP in
    another colour, then the dark empty track. HP is the fill's share of the inside.
    """
    h, w, _ = a.shape
    y_lo, y_hi = int(0.25 * h), int(0.8 * h)
    band = a[y_lo:y_hi].astype(np.int16)
    mx, mn = band.max(axis=2), band.min(axis=2)
    r, g, b = band[..., 0], band[..., 1], band[..., 2]
    # the border is light (tinted blue in night battles), but flashes orange while that Pokémon is being hit
    white = (mn > 150) & (mx - mn < 60)
    night = (mn > 110) & (b > 220) & (b >= g) & (g > r)
    light = white | night | ((r > 200) & (g > 100) & (g < 190) & (b < 90))
    lines = []  # (y, x0, x1): border-coloured runs as long as a bar
    for y in range(band.shape[0]):
        seg = np.concatenate(([False], light[y], [False]))
        edges = np.flatnonzero(np.diff(seg.astype(np.int8)))
        lines += [(y, int(s), int(t)) for s, t in zip(edges[0::2], edges[1::2]) if 0.25 * w <= t - s <= 0.33 * w]
    bars, used_until = [], -1
    for i, (y, x0, x1) in enumerate(lines):
        if y <= used_until:
            continue
        for y2, u0, u1 in lines[i + 1:]:
            gap = y2 - y
            if gap > 0.035 * w:
                break
            overlap = min(x1, u1) - max(x0, u0)
            if gap >= 0.018 * w and overlap >= 0.95 * min(x1 - x0, u1 - u0):
                x0, x1 = max(x0, u0), min(x1, u1)
                if min(x0, u0) <= 2 or max(x1, u1) >= w - 2:
                    break  # cut off by the screen's edge, so its fill can't be measured
                mid = band[(y + y2) // 2, x0:x1]
                m_mx, m_mn = mid.max(axis=1), mid.min(axis=1)
                colour = (m_mx > 150) & (m_mx - m_mn > 80) & (m_mn < 120)
                track = m_mx < 70
                inside = np.flatnonzero(colour | track)
                if len(inside) < 0.8 * (x1 - x0):
                    break  # mostly neither fill nor track: not a bar after all
                last = inside[-1] + 1
                m_r, m_g, m_b = mid[:, 0], mid[:, 1], mid[:, 2]
                orange = (m_r > 200) & (m_g > 100) & (m_g < 190) & (m_b < 90)  # hit flash / just-lost HP, never fill
                solid = colour & ~orange
                run4 = solid[:-3] & solid[1:-2] & solid[2:-1] & solid[3:]
                starts = np.flatnonzero(run4[: int(0.15 * (x1 - x0))])
                if len(starts):
                    first = int(starts[0])
                    fill = np.abs(mid[first:last] - mid[min(first + 4, last - 1)]).max(axis=1) < 45
                    fill[:4] = True
                    n = int(np.argmin(fill)) if not fill.all() else last - first
                else:
                    first, n = int(inside[0]), 0
                bars.append((x0, x1, y_lo + (y + y2) // 2, n / (last - first)))
                used_until = y2
                break
    return sorted(bars, key=lambda bar: bar[2])


def read_hp(image: np.ndarray) -> HpReading:
    """Your Pokémon is nearer the camera, so its bar is the lower one."""
    bars = hp_bars(image)
    if len(bars) >= 2:
        return HpReading(round(bars[-1][3], 3), round(bars[0][3], 3), None)
    if bars:
        return HpReading(None, None, round(bars[0][3], 3))
    return HpReading(None, None, None)


# --- charged move buttons ---------------------------------------------------------------------------------------
@dataclass(frozen=True)
class ButtonReading:
    """A button's fill (0..1 of its move's cost; 1 = ready) and, when its label was read, the move's ID."""
    fill: float | None
    move_id: str | None = None


def _button_geometry(image: np.ndarray) -> list[tuple[int, int, int]]:
    h, w = image.shape[:2]
    r = int(BUTTON_RADIUS * w)
    return [(int(fx * w), h - int(fy * w), r) for fx, fy in BUTTON_CENTRES]


def _has_ring(image: np.ndarray, cx: int, cy: int, r: int) -> bool:
    """
    The light ring around a button, on screen for at least half the way round (the Pokémon can cover the rest). The
    ring is translucent, so it's found by contrast: brighter than just inside and just outside it, not by a set level.
    """
    h, w = image.shape[:2]

    def level(a: float, ks) -> float | None:
        vals = []
        for k in ks:
            x, y = int(cx + k * r * math.cos(a)), int(cy + k * r * math.sin(a))
            if 0 <= x < w and 0 <= y < h:
                vals.append(int(image[y, x].min()))
        return vals

    lit = 0
    for deg in range(0, 360, 10):
        a = math.radians(deg)
        ring = level(a, np.linspace(1.5, 1.8, 7))
        around = level(a, (1.3, 1.35, 1.95, 2.0))
        if ring and around and max(ring) - float(np.median(around)) > 20:
            lit += 1
    return lit >= 18


def _column_fill(image: np.ndarray, cx: int, cy: int, r: int, dx: float) -> float | None:
    """
    How high the fill reaches along one vertical line through the disc: the split of the line (bottom = filled,
    top = empty) with the biggest brightness difference. A line that's all one brightness is full or empty.
    """
    half = int(math.sqrt(1 - dx * dx) * r * 0.95)
    x = int(cx + dx * r)
    ys = np.arange(cy + half, cy - half, -1)  # bottom to top
    if x < 0 or x >= image.shape[1] or ys.min() < 0 or ys.max() >= image.shape[0]:
        return None
    lum = image[ys, x].max(axis=1).astype(float)
    n = len(lum)
    cum = np.cumsum(lum)
    best, split = 0.0, None
    for s in range(2, n - 1):
        diff = cum[s - 1] / s - (cum[-1] - cum[s - 1]) / (n - s)
        if diff > best:
            best, split = diff, s
    if best > 35 and split is not None:
        y_line = cy + half - split
        return float(np.clip((cy + r - y_line) / (2 * r), 0, 1))
    return 1.0 if lum.mean() > 110 else 0.0


def charge_buttons(image: np.ndarray) -> list[ButtonReading]:
    """Both buttons, left then right; fill None when the button isn't on screen."""
    out = []
    for cx, cy, r in _button_geometry(image):
        if not _has_ring(image, cx, cy, r):
            out.append(ButtonReading(None))
            continue
        # off-centre lines, so the move's icon in the middle doesn't count as fill
        fills = [f for f in (_column_fill(image, cx, cy, r, dx) for dx in (-0.65, -0.55, 0.55, 0.65)) if f is not None]
        out.append(ButtonReading(round(float(np.median(fills)), 3) if fills else None))
    return out


def button_moves(image: np.ndarray) -> list[str | None]:
    """The move IDs from the labels under the buttons ("BULLDOZE", "ROCK TOMB"): light text on the dark field."""
    moves = []
    h, w = image.shape[:2]
    for cx, cy, r in _button_geometry(image):
        crop = image[max(0, cy + int(1.2 * r)): min(h, cy + int(1.9 * r)), max(0, cx - int(2.4 * r)): min(w, cx + int(2.4 * r))]
        if crop.size == 0:
            moves.append(None)
            continue
        grey = cv2.resize(crop.max(axis=2), None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC)
        _, bw = cv2.threshold(grey, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)  # light text -> black on white
        bw = cv2.copyMakeBorder(bw, 20, 20, 20, 20, cv2.BORDER_CONSTANT, value=255)
        text = ' '.join(word.text for word in ocr.words(cv2.cvtColor(bw, cv2.COLOR_GRAY2RGB), psm=7))
        moves.append(match_move(text) if text else None)
    return moves


# --- Poké Balls and shields -------------------------------------------------------------------------------------
def _count_runs(cols: np.ndarray, min_width: int) -> int:
    """How many separate runs of True a column profile has (one per icon)."""
    runs, width = 0, 0
    for on in list(cols) + [False]:
        if on:
            width += 1
        else:
            if width >= min_width:
                runs += 1
            width = 0
    return runs


def icon_counts(image: np.ndarray, panel: Panel) -> tuple[int, int]:
    """(Poké Balls, shields) in the panel's lower row: red balls are Pokémon not yet fainted, pink hexes shields."""
    icons = image[panel.y + int(0.5 * panel.h): panel.y + panel.h, panel.x: panel.x + panel.w].astype(int)
    r, g, b = icons[..., 0], icons[..., 1], icons[..., 2]
    red = (r > 190) & (g < 90) & (b < 90)
    pink = (r > 170) & (b > 170) & (g < r - 25)
    min_width = max(3, int(0.025 * panel.w))
    return _count_runs(red.sum(axis=0) > 2, min_width), _count_runs(pink.sum(axis=0) > 2, min_width)


# --- shields in use -----------------------------------------------------------------------------------------------
def _pink_share(region: np.ndarray) -> float:
    a = region.astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    return float(((r > 170) & (b > 170) & (g < r - 25)).mean())


def _shield_share(region: np.ndarray) -> float:
    """Share of the shield's colours: its hexagons shade from pink to lavender."""
    a = region.astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    return float(((b > 170) & (r > 130) & (g < b - 25) & (r > g + 10)).mean())


def shield_prompt(image: np.ndarray) -> bool:
    """"Use a Protect Shield?": the big pink-to-lavender hexagon button at the bottom centre."""
    h, w = image.shape[:2]
    return _shield_share(image[int(0.76 * h): int(0.92 * h), int(0.38 * w): int(0.62 * w)]) > 0.2


def shield_bubble(image: np.ndarray) -> bool:
    """A shield going up: a pink bubble over the defender mid-screen (and no prompt button)."""
    h, w = image.shape[:2]
    return not shield_prompt(image) and _pink_share(image[int(0.25 * h): int(0.75 * h), int(0.05 * w): int(0.95 * w)]) > 0.04


# --- the switch timer ---------------------------------------------------------------------------------------------
def _bench_card(image: np.ndarray) -> tuple[int, int, int] | None:
    """
    The top bench icon on the right as (x, y, size): a pale, nearly colourless square card. The timer's wedge darkens
    part of it, but the part just before 12 o'clock stays pale and reaches the card's top and left edges, so the
    pale area's top-left corner and its larger side give the card.
    """
    h, w = image.shape[:2]
    x_lo, y_lo = int(0.78 * w), int(0.5 * h)
    region = image[y_lo: int(0.8 * h), x_lo:].astype(np.int16)
    pale = ((region.min(axis=2) > 140) & (region.max(axis=2) - region.min(axis=2) < 50)).astype(np.uint8)
    n, _, stats, _ = cv2.connectedComponentsWithStats(pale)
    cards = [s for s in stats[1:] if 0.06 * w <= max(s[2], s[3]) <= 0.22 * w and s[4] > 0.2 * max(s[2], s[3]) ** 2]
    if not cards:
        return None
    x, y, cw, ch, _ = min(cards, key=lambda s: s[1])
    return x_lo + int(x), y_lo + int(y), int(max(cw, ch))


def switch_timer(image: np.ndarray) -> float | None:
    """
    Seconds until you can switch, from the wedge sweeping clockwise from 12 o'clock over the bench icons (its share
    of the circle is the share of the 50 s gone). 0 when the cards show no wedge (ready); None with no bench on screen.
    """
    card = _bench_card(image)
    if not card:
        return None
    x, y, s = card
    cx, cy = x + s / 2, y + s / 2
    lum = []
    for deg in range(0, 360, 2):
        a = math.radians(deg)
        vals = []
        for k in np.linspace(0.18, 0.42, 6):
            px, py = int(cx + k * s * math.sin(a)), int(cy - k * s * math.cos(a))
            if 0 <= px < image.shape[1] and 0 <= py < image.shape[0]:
                vals.append(int(image[py, px].max()))
        lum.append(np.mean(vals) if vals else np.nan)
    lum = np.array(lum)
    if np.nanmax(lum) - np.nanmin(lum) < 40:
        return 0.0
    dark = lum < (np.nanmax(lum) + np.nanmin(lum)) / 2
    if not dark[:4].any():
        return 0.0  # darkness elsewhere (something passing behind the card) isn't the wedge
    swept = 0
    while swept < len(dark) and dark[swept]:
        swept += 1
    return round(SWITCH_TIMER_S * (1 - swept / len(dark)), 1)

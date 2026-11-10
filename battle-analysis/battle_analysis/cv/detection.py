"""
Who is on the field, and when that changes.

Species are identified from the HUD: the game prints each active Pokémon's name in its top panel ("Swampert CP 1497"
on your side, top left; "CP 1469 Weezing" on the opponent's, top right), and announces switch-ins ("Go, Cradily!")
and charged moves ("Togekiss used Ancient Power!") in a banner. Reading that text is exact where a trained detector
would only be probably right about a 3D model seen from changing camera angles, so this uses OCR plus the game data's
names instead of a YOLO model. A name OCR can't place (a nickname) is left unknown rather than guessed.

Switches and faints come from the per-turn readings: a different species that stays out for 2+ turns is a switch; HP
at 0 or a lost Poké Ball in the panel is a faint, and the next species is a forced switch-in.
"""
import difflib
import re
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from functools import cache

import cv2
import numpy as np

from battle_analysis import gamedata
from battle_analysis.cv import ocr
from battle_analysis.cv.model import TurnReading

Matcher = Callable[[str], str | None]


# --- names ------------------------------------------------------------------------------------------------------
@cache
def _names() -> dict[str, str]:
    """Normalised on-screen name -> species name."""
    names = {}
    for name in gamedata.species():
        m = re.match(r'(.+?) \((.+)\)$', name)
        if m and 'Mega' in m.group(2):  # the HUD says "Mega Altaria", the gamemaster "Altaria (Mega)"
            names.setdefault(gamedata.norm('Mega ' + m.group(1)), name)
        base = m.group(1) if m else name  # forms show without the form: "Stunfisk", not "Stunfisk (Galarian)"
        names.setdefault(gamedata.norm(base), base)
    return names


def species_matcher() -> Matcher:
    names = _names()
    keys = list(names)

    def match(text: str) -> str | None:
        t = gamedata.norm(text)
        if len(t) < 3:
            return None
        # the longest species the text starts with: OCR adds junk at the end ("Weezingl")
        best = max((k for k in keys if len(k) >= 3 and t.startswith(k)), key=len, default=None)
        if best:
            return names[best]
        # misread letters: the closest spelling, comparing the text with and without one junk character at the end
        for candidate in (t, t[:-1]):
            close = difflib.get_close_matches(candidate, keys, n=1, cutoff=0.8)
            if close:
                return names[close[0]]
        return None

    return match


@cache
def _charged_move_names() -> dict[str, str]:
    return {gamedata.norm(m.name): m.id for m in gamedata.moves().values() if not m.is_fast}


def match_move(text: str) -> str | None:
    """A charged move's ID from its OCR'd name, allowing a misread letter or a stray character at the end."""
    names = _charged_move_names()
    t = gamedata.norm(text)
    if t in names:
        return names[t]
    best, best_ratio = None, 0.0
    for candidate in (t, t[:-1], t[:-2]):
        for key in difflib.get_close_matches(candidate, list(names), n=1, cutoff=0.82):
            ratio = difflib.SequenceMatcher(None, candidate, key).ratio()
            if ratio > best_ratio:
                best, best_ratio = names[key], ratio
    return best


# --- banners ------------------------------------------------------------------------------------------------------
@dataclass(frozen=True)
class SwitchInBanner:
    """"Go, X!": your Pokémon X comes in."""
    species: str


@dataclass(frozen=True)
class MoveBanner:
    """"X used Y!": a charged move was thrown (by either side; the reconstructor works out whose from the species)."""
    species: str | None
    move: str
    move_id: str


@dataclass(frozen=True)
class AttackIncoming:
    """"Attack incoming!": the opponent threw a charged move and you're about to be asked whether to shield."""


Banner = SwitchInBanner | MoveBanner | AttackIncoming


def parse_banner(text: str, match: Matcher) -> Banner | None:
    text = ' '.join(text.split())
    if re.search(r'attack\s*incom', text, re.I):
        return AttackIncoming()
    m = re.match(r'^Go[,.]?\s+(.+)$', text)
    if m:
        sp = match(m.group(1))
        return SwitchInBanner(sp) if sp else None
    m = re.match(r'^(.+?)\s+used\s+(.+)$', text, re.I)
    if m:
        move_id = match_move(m.group(2))
        if move_id:
            return MoveBanner(match(m.group(1)), gamedata.moves()[move_id].name, move_id)
    return None


def read_banner(image: np.ndarray, match: Matcher) -> Banner | None:
    """The centre-screen announcement, if one is up (it sits around 10-40% of the height, varying with the camera)."""
    h = image.shape[0]
    for line in ocr.lines(image[int(0.08 * h): int(0.42 * h)]):
        banner = parse_banner(line, match)
        if banner:
            return banner
    return None


# --- the switch-in flash ------------------------------------------------------------------------------------------
def _white_share(image: np.ndarray) -> float:
    return float((image.min(axis=2) > 215).mean())


def is_switch_flash(prev: np.ndarray, cur: np.ndarray) -> bool:
    """The screen whiting out from one turn to the next: mostly white now, mostly not before, and much brighter."""
    return _white_share(cur) >= 0.4 and _white_share(prev) < 0.2 and float(cur.mean()) - float(prev.mean()) > 60


# --- the top panels -----------------------------------------------------------------------------------------------
@dataclass(frozen=True)
class Panel:
    """One side's top panel: the name as read, the species it is, the CP, and the panel's box in the game image."""
    name: str
    species: str | None
    cp: int | None
    x: int
    y: int
    w: int
    h: int


def find_panels(image: np.ndarray) -> tuple[tuple[int, int, int, int], tuple[int, int, int, int]] | None:
    """
    The two HUD panels' boxes (x, y, w, h), yours (left) first: light grey, nearly colourless boxes about 38% of the
    width and 5% of the height, in the top fifth of the screen. None unless both are there.
    """
    h, w = image.shape[:2]
    band = image[: int(0.2 * h)].astype(np.int16)
    light = ((band.min(axis=2) > 175) & (band.max(axis=2) - band.min(axis=2) < 45)).astype(np.uint8)
    n, _, stats, _ = cv2.connectedComponentsWithStats(light)
    boxes = [tuple(int(v) for v in s[:4]) for s in stats[1:]
             if 0.25 * w <= s[2] <= 0.48 * w and 0.025 * h <= s[3] <= 0.09 * h and s[4] >= 0.5 * s[2] * s[3]]
    left = [b for b in boxes if b[0] + b[2] / 2 < w / 2]
    right = [b for b in boxes if b[0] + b[2] / 2 >= w / 2]
    if not (left and right):
        return None
    return min(left, key=lambda b: b[1]), min(right, key=lambda b: b[1])


def _is_cp_label(text: str) -> bool:
    return re.fullmatch(r'[CG]P\d*', re.sub(r'[^A-Za-z0-9]', '', text), re.I) is not None


def _binarize(image: np.ndarray, scale: int = 3) -> np.ndarray:
    """
    Dark text on a light panel as clean black on white: the darkest channel (the HUD's teal and grey text are dark in
    at least one), upscaled (Tesseract wants letters ~30 px tall), Otsu-thresholded, with a white margin. On the real
    recordings this took the opponent's name from 17 of 56 frames read right to 55 of 56.
    """
    grey = cv2.resize(image.min(axis=2), None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    _, bw = cv2.threshold(grey, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    bw = cv2.copyMakeBorder(bw, 20, 20, 20, 20, cv2.BORDER_CONSTANT, value=255)
    return cv2.cvtColor(bw, cv2.COLOR_GRAY2RGB)


def _read_panel(image: np.ndarray, box: tuple[int, int, int, int], match: Matcher) -> Panel:
    """The name and CP are on the panel's top line (its top 55%)."""
    x, y, w, h = box
    line_img = _binarize(image[y: y + int(0.55 * h), x: x + w])
    found = ocr.words(line_img, psm=7)
    cp, name_words = None, []
    for k, word in enumerate(found):
        clean = re.sub(r'[^A-Za-z0-9]', '', word.text)
        if _is_cp_label(word.text):
            digits = re.sub(r'\D', '', clean)
            if digits:
                cp = int(digits)
            elif k + 1 < len(found) and re.fullmatch(r'\d{2,5}', re.sub(r'\D', '', found[k + 1].text) or 'x'):
                cp = int(re.sub(r'\D', '', found[k + 1].text))
        elif clean and not clean.isdigit():
            name_words.append(word.text)
    name = ' '.join(name_words)
    return Panel(name, match(name), cp, x, y, w, h)


def read_panels(image: np.ndarray, match: Matcher) -> tuple[Panel, Panel] | None:
    """(yours, the opponent's) from the top of the screen; None when the HUD isn't showing."""
    boxes = find_panels(image)
    if not boxes:
        return None
    return _read_panel(image, boxes[0], match), _read_panel(image, boxes[1], match)


# --- switches and faints ------------------------------------------------------------------------------------------
@dataclass(frozen=True)
class Event:
    turn: int
    timestamp_ms: int
    side: str           # 'user' or 'opp'
    kind: str           # 'switch' (chosen), 'faint', or 'switch_in' (forced, after a faint)
    species: str        # who came in (switch, switch_in) or went down (faint)
    previous: str | None = None


def _active_species(readings: Sequence[TurnReading], side: str) -> list[str | None]:
    """
    The species out on one side at each reading: a new name counts once it's read on two readings in a row (or a
    "Go, X!" banner names it), so a one-frame misread isn't a switch there and back.
    """
    seq = [(i, getattr(r, side).species) for i, r in enumerate(readings)
           if getattr(r, side) is not None and getattr(r, side).species]
    banner_at = {i: r.banner.species for i, r in enumerate(readings) if isinstance(r.banner, SwitchInBanner)}
    change_at: dict[int, str] = {}
    current = None
    for k, (i, sp) in enumerate(seq):
        if sp == current:
            continue
        confirmed = k + 1 < len(seq) and seq[k + 1][1] == sp
        if current is None or confirmed or (side == 'user' and banner_at.get(i) == sp):
            current = sp
            change_at[i] = sp
    active, current = [], None
    for i in range(len(readings)):
        current = change_at.get(i, current)
        active.append(current)
    return active


def _side_events(readings: Sequence[TurnReading], side: str) -> list[Event]:
    active = _active_species(readings, side)
    events = []
    hp_zero_at = None          # the first reading where the active Pokémon's HP read empty
    ball_drop_at = None
    last_balls = None
    start = None
    for i, r in enumerate(readings):
        s = getattr(r, side)
        sp = active[i]
        if start is not None and sp != active[start]:
            prev = active[start]
            fainted = ball_drop_at is not None or (hp_zero_at is not None and i - hp_zero_at <= 8)
            if fainted:
                at = hp_zero_at if hp_zero_at is not None else ball_drop_at
                events.append(Event(readings[at].turn, readings[at].timestamp_ms, side, 'faint', prev))
                events.append(Event(r.turn, r.timestamp_ms, side, 'switch_in', sp, prev))
            else:
                events.append(Event(r.turn, r.timestamp_ms, side, 'switch', sp, prev))
            hp_zero_at = ball_drop_at = None
            start = i
        if start is None and sp is not None:
            start = i
        if s is None:
            continue
        if s.hp is not None:
            if s.hp <= 0.02 and hp_zero_at is None:
                hp_zero_at = i
            elif s.hp > 0.05:
                hp_zero_at = None
        if s.balls:
            if last_balls is not None and s.balls < last_balls and ball_drop_at is None:
                ball_drop_at = i
            last_balls = s.balls
    # the last Pokémon going down ends the battle with no switch-in after it
    if start is not None and hp_zero_at is not None:
        r = readings[hp_zero_at]
        events.append(Event(r.turn, r.timestamp_ms, side, 'faint', active[start]))
    return events


def detect_events(readings: Sequence[TurnReading]) -> list[Event]:
    """Every switch, faint and forced switch-in on both sides, in turn order."""
    return sorted(_side_events(readings, 'user') + _side_events(readings, 'opp'), key=lambda e: (e.turn, e.side))


def teams_from(readings: Sequence[TurnReading]) -> tuple[list[str], list[str]]:
    """Each side's first three different species to come out (after the misread filter), in order."""
    teams = []
    for side in ('user', 'opp'):
        team = []
        for sp in _active_species(readings, side):
            if sp and sp not in team and len(team) < 3:
                team.append(sp)
        teams.append(team)
    return teams[0], teams[1]

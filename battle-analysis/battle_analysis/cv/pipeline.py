"""
The whole CV pass: video file -> turn_log dict.

Frames are decoded in order on this thread (the flash check compares each with the one before); the OCR-heavy
reading of each frame runs on a small thread pool, since Tesseract runs as a subprocess and doesn't hold the GIL.
At most `workers * 4` frames wait in memory at once.
"""
from collections import deque
from collections.abc import Callable
from concurrent.futures import Future, ThreadPoolExecutor
from pathlib import Path

import numpy as np

from battle_analysis.cv import detection, hud
from battle_analysis.cv.model import SideReading, TurnReading
from battle_analysis.cv.reconstruct import build_turn_log
from battle_analysis.cv.video import read_ticks, video_info

Progress = Callable[[float, str], None]


def read_turn(turn: int, timestamp_ms: int, img: np.ndarray, match: detection.Matcher) -> TurnReading:
    """Everything the readers can see on one frame (the game area of a normalized frame)."""
    r = TurnReading(turn=turn, timestamp_ms=timestamp_ms)
    panels = detection.read_panels(img, match)
    if panels:
        sides = []
        for p in panels:
            balls, shields = hud.icon_counts(img, p)
            sides.append(SideReading(p.species, p.cp, None, balls, shields))
        r.user, r.opp = sides
    hp = hud.read_hp(img)
    if hp.user is not None and r.user:
        r.user.hp, r.opp.hp = hp.user, hp.opp
    elif hp.user is not None:
        r.lone_hp = None  # both bars but no panels: sides can't be named, the reconstructor skips it
    else:
        r.lone_hp = hp.lone
    r.energy_fill = [b.fill for b in hud.charge_buttons(img)]
    r.shield_prompt = hud.shield_prompt(img)
    r.shielding = hud.shield_bubble(img)
    if not r.shield_prompt:
        r.banner = detection.read_banner(img, match)
    r.switch_wedge = hud.switch_timer(img)
    return r


def analyze_video(path: str | Path, match_id: str, progress: Progress | None = None, workers: int = 4) -> dict:
    """The turn_log for a recording. `progress(share, stage)` is called as turns are read (share of the CV pass, 0..1)."""
    match = detection.species_matcher()
    total = max(1, video_info(path)['turns'])
    readings: list[TurnReading] = []
    label_frames: dict[int, np.ndarray] = {}  # a few frames with the buttons showing, to read the move labels from
    pending: deque[tuple[Future, bool]] = deque()
    prev = None

    def collect(block: bool):
        while pending and (block or pending[0][0].done()):
            future, flash = pending.popleft()
            reading = future.result()
            reading.flash = flash
            readings.append(reading)
            if progress and len(readings) % 10 == 0:
                progress(min(1.0, len(readings) / total), 'processing_cv')

    with ThreadPoolExecutor(max_workers=workers) as pool:
        for tick in read_ticks(path):
            if tick.image is None:
                readings_future: Future = Future()
                readings_future.set_result(TurnReading(tick.turn, tick.timestamp_ms, dropped=True))
                pending.append((readings_future, False))
            else:
                img = tick.content.crop(tick.image)
                flash = prev is not None and detection.is_switch_flash(prev, img)
                prev = img
                pending.append((pool.submit(read_turn, tick.turn, tick.timestamp_ms, img, match), flash))
                if tick.turn % 4 == 0:
                    label_frames[tick.turn] = img
            collect(block=len(pending) >= workers * 4)
            # keep only frames whose turn turned out to show the buttons
            for t in [t for t in label_frames if t < len(readings) and not any(readings[t].energy_fill or [])]:
                del label_frames[t]
        collect(block=True)

    _read_move_labels(readings, label_frames)
    if progress:
        progress(1.0, 'reconstructing')
    log = build_turn_log(readings, match_id)
    info = video_info(path)
    log['video'] = {'fps': round(info['fps'], 2), 'duration_ms': int(info['duration_ms']), 'turns_read': len(readings),
                    'turns_dropped': sum(r.dropped for r in readings)}
    return log


def _read_move_labels(readings: list[TurnReading], frames: dict[int, np.ndarray]) -> None:
    """
    The move names under the buttons, read once per species of yours (OCR on a few frames, most common answer),
    then set on every reading while that species is out. Without them energy falls back to the usual moveset's costs.
    """
    by_species: dict[str, list[list[str | None]]] = {}
    for t, img in sorted(frames.items()):
        r = readings[t] if t < len(readings) else None
        if not (r and r.user and r.user.species and any(f is not None for f in r.energy_fill)):
            continue
        tries = by_species.setdefault(r.user.species, [])
        if len(tries) < 3:
            tries.append(hud.button_moves(img))
    chosen = {}
    for sp, tries in by_species.items():
        slots = []
        for k in range(2):
            votes = [t[k] for t in tries if len(t) > k and t[k]]
            slots.append(max(set(votes), key=votes.count) if votes else None)
        chosen[sp] = slots
    current = None
    for r in readings:
        if r.user and r.user.species:
            current = r.user.species
        if current in chosen:
            r.charge_moves = chosen[current]

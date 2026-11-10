"""
Video ingestion: turns an uploaded screen recording into one normalized frame per GBL turn.

GBL's server runs in 500 ms turns, so the reader samples the video at 2 Hz: turn k is the frame shown at k * 500 ms.
Every frame is letterboxed to 720x1280 (portrait 9:16), keeping its aspect ratio; the box the game actually occupies
is returned with it, so the HUD readers measure positions as fractions of the game, not of the padding.
"""
from collections.abc import Iterator, Sequence
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np

TARGET_W, TARGET_H = 720, 1280
TURN_MS = 500
# a frame further than this from a turn's time doesn't show that turn
TICK_TOLERANCE_MS = TURN_MS / 2


@dataclass(frozen=True)
class ContentBox:
    """Where the game sits inside the 720x1280 frame (the rest is black padding)."""
    x: int
    y: int
    w: int
    h: int

    def crop(self, image: np.ndarray) -> np.ndarray:
        return image[self.y:self.y + self.h, self.x:self.x + self.w]


@dataclass(frozen=True)
class TickSlot:
    """Turn `turn` (at `timestamp_ms`) is shown by source frame `frame_index`, or by none when frames were dropped."""
    turn: int
    timestamp_ms: int
    frame_index: int | None

    @property
    def dropped(self) -> bool:
        return self.frame_index is None


@dataclass(frozen=True)
class FrameGap:
    """A stretch with no frames: the recorder hitched between `start_ms` and `end_ms`."""
    start_ms: float
    end_ms: float
    missing_frames: int


@dataclass(frozen=True)
class TickFrame:
    """One turn's frame, RGB 720x1280, or image None when the recording has no frame for that turn."""
    turn: int
    timestamp_ms: int
    image: np.ndarray | None
    content: ContentBox
    source_ms: float | None

    @property
    def dropped(self) -> bool:
        return self.image is None


def letterbox(frame_bgr: np.ndarray) -> tuple[np.ndarray, ContentBox]:
    """The frame scaled to fit 720x1280 without stretching, centred on black, as RGB; and where the content is."""
    h, w = frame_bgr.shape[:2]
    scale = min(TARGET_W / w, TARGET_H / h)
    new_w, new_h = min(TARGET_W, round(w * scale)), min(TARGET_H, round(h * scale))
    # INTER_AREA when shrinking keeps thin HUD lines (the HP bar borders) instead of skipping pixels
    interpolation = cv2.INTER_AREA if scale < 1 else cv2.INTER_LINEAR
    resized = cv2.resize(frame_bgr, (new_w, new_h), interpolation=interpolation)
    x, y = (TARGET_W - new_w) // 2, (TARGET_H - new_h) // 2
    out = np.zeros((TARGET_H, TARGET_W, 3), np.uint8)
    out[y:y + new_h, x:x + new_w] = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB)
    return out, ContentBox(x, y, new_w, new_h)


class TickClock:
    """
    Picks, online, which frames show which turns: the first frame at or after a turn's time (less half a frame, so a
    frame 1 ms early still counts) shows it, if it's within TICK_TOLERANCE_MS. A turn whose nearest frame is further
    away was lost to a recording hitch, and is dropped rather than shown with another turn's frame.
    """

    def __init__(self, frame_interval_ms: float):
        self.half_frame = frame_interval_ms / 2
        self.next_turn = 0

    def feed(self, timestamp_ms: float, frame_index: int) -> list[TickSlot]:
        slots = []
        while timestamp_ms >= self.next_turn * TURN_MS - self.half_frame:
            target = self.next_turn * TURN_MS
            self.next_turn += 1
            if timestamp_ms <= target + TICK_TOLERANCE_MS:
                slots.append(TickSlot(self.next_turn - 1, target, frame_index))
                break  # a frame shows one turn at most
            slots.append(TickSlot(self.next_turn - 1, target, None))
        return slots


def _interval(timestamps_ms: Sequence[float]) -> float:
    """The usual time between frames (the median gap, so a hitch doesn't skew it)."""
    if len(timestamps_ms) < 2:
        return 1000 / 30
    return float(np.median(np.diff(timestamps_ms)))


def tick_plan(timestamps_ms: Sequence[float]) -> list[TickSlot]:
    """Which frame (by index) shows each turn, for frames shown at these times."""
    clock = TickClock(_interval(timestamps_ms))
    plan = []
    for i, ts in enumerate(timestamps_ms):
        plan += clock.feed(ts, i)
    return plan


def find_frame_drops(timestamps_ms: Sequence[float], fps: float) -> list[FrameGap]:
    """Gaps between frames longer than 1.5 frame intervals: the recorder dropped frames there."""
    interval = 1000 / fps
    gaps = []
    for a, b in zip(timestamps_ms, timestamps_ms[1:]):
        if b - a > 1.5 * interval:
            gaps.append(FrameGap(a, b, round((b - a) / interval) - 1))
    return gaps


def read_ticks(path: str | Path) -> Iterator[TickFrame]:
    """
    The video's frames at 2 Hz, one per turn from 0 ms, normalized. Streams: only the chosen frames are converted,
    so memory stays flat however long the video is. Raises ValueError for a file that isn't a readable video.
    """
    capture = cv2.VideoCapture(str(path))
    if not capture.isOpened():
        raise ValueError(f'Not a readable video: {Path(path).name}')
    fps = capture.get(cv2.CAP_PROP_FPS) or 30
    clock = TickClock(1000 / fps)
    box = ContentBox(0, 0, TARGET_W, TARGET_H)
    index = 0
    try:
        while capture.grab():
            # the decoder's own timestamp, so variable-frame-rate phone recordings keep real time
            ts = capture.get(cv2.CAP_PROP_POS_MSEC)
            if ts <= 0 and index > 0:
                ts = index * 1000 / fps  # some containers don't report it
            for slot in clock.feed(ts, index):
                if slot.dropped:
                    yield TickFrame(slot.turn, slot.timestamp_ms, None, box, None)
                    continue
                ok, frame = capture.retrieve()
                if not ok:
                    yield TickFrame(slot.turn, slot.timestamp_ms, None, box, None)
                    continue
                image, box = letterbox(frame)
                yield TickFrame(slot.turn, slot.timestamp_ms, image, box, ts)
            index += 1
    finally:
        capture.release()


def video_info(path: str | Path) -> dict:
    """Frame rate, size and length as the container reports them (for logs and the job's progress estimate)."""
    capture = cv2.VideoCapture(str(path))
    if not capture.isOpened():
        raise ValueError(f'Not a readable video: {Path(path).name}')
    try:
        fps = capture.get(cv2.CAP_PROP_FPS) or 30
        frames = int(capture.get(cv2.CAP_PROP_FRAME_COUNT))
        return {
            'fps': fps,
            'width': int(capture.get(cv2.CAP_PROP_FRAME_WIDTH)),
            'height': int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT)),
            'frames': frames,
            'duration_ms': frames * 1000 / fps if fps else 0,
            'turns': int(frames * 1000 / fps // TURN_MS) + 1 if fps else 0,
        }
    finally:
        capture.release()

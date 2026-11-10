"""Issue #93: frame normalization and video ingestion."""
import cv2
import numpy as np
import pytest

from battle_analysis.cv.video import (
    TARGET_H,
    TARGET_W,
    ContentBox,
    find_frame_drops,
    letterbox,
    read_ticks,
    tick_plan,
)


def write_video(path, fps, seconds, size=(390, 844)):
    """A synthetic clip (not a real recording): frame i is a flat grey of level 2*i, so a decoded frame says which it was."""
    w, h = size
    writer = cv2.VideoWriter(str(path), cv2.VideoWriter_fourcc(*'mp4v'), fps, (w, h))
    assert writer.isOpened()
    for i in range(int(fps * seconds)):
        writer.write(np.full((h, w, 3), min(255, 2 * i), np.uint8))
    writer.release()


# An iPhone recording (1170x2532, taller than 9:16) is scaled to the full 1280 height and centred with black bars left
# and right: 2532 -> 1280 is x0.5055, so the width 1170 becomes 591 and sits 64 px in from the left.
def test_tall_phone_recording_is_scaled_to_full_height_and_centred():
    frame = np.full((2532, 1170, 3), (0, 0, 255), np.uint8)  # pure red in BGR
    image, box = letterbox(frame)
    assert image.shape == (TARGET_H, TARGET_W, 3)
    assert box == ContentBox(x=64, y=0, w=591, h=1280)
    # output is RGB: the content is red, the bars are black
    assert tuple(image[640, 360]) == (255, 0, 0)
    assert tuple(image[640, 10]) == (0, 0, 0)
    assert tuple(image[640, 710]) == (0, 0, 0)


# A landscape clip (1920x1080) fits by width instead: x0.375 gives 720x405, centred vertically 437 px down.
def test_landscape_video_is_scaled_to_full_width_and_centred_vertically():
    image, box = letterbox(np.full((1080, 1920, 3), 200, np.uint8))
    assert image.shape == (1280, 720, 3)
    assert box == ContentBox(x=0, y=437, w=720, h=405)
    assert image[100, 360].tolist() == [0, 0, 0]
    assert image[640, 360].tolist() == [200, 200, 200]


# At 30 fps a turn (500 ms) is 15 frames, so turns 0..4 are frames 0, 15, 30, 45, 60.
def test_30fps_frames_map_to_one_frame_per_turn():
    stamps = [i * 1000 / 30 for i in range(61)]
    plan = tick_plan(stamps)
    assert [(s.turn, s.timestamp_ms, s.frame_index) for s in plan] == [
        (0, 0, 0), (1, 500, 15), (2, 1000, 30), (3, 1500, 45), (4, 2000, 60)]


# At 60 fps a turn is 30 frames: the same turns come from frames 0, 30, 60, 90.
def test_60fps_frames_map_to_one_frame_per_turn():
    stamps = [i * 1000 / 60 for i in range(100)]
    assert [s.frame_index for s in tick_plan(stamps)] == [0, 30, 60, 90]


# A recording that hitches loses ~0.9 s of frames (none between 900 and 1800 ms). Turns 2 and 3 (1000 and 1500 ms)
# have no frame within 250 ms, so they are marked dropped (no frame) instead of borrowing a frame from another turn.
def test_turns_inside_a_frame_gap_are_dropped_not_borrowed():
    stamps = [i * 1000 / 30 for i in range(28)] + [1800 + i * 1000 / 30 for i in range(20)]
    plan = tick_plan(stamps)
    by_turn = {s.turn: s for s in plan}
    assert by_turn[1].frame_index == 15
    assert by_turn[2].frame_index is None and by_turn[2].dropped
    assert by_turn[3].frame_index is None and by_turn[3].dropped
    # turn 4 (2000 ms) is the frame at 1800 + 6 * 33.3 = 2000 ms, index 28 + 6
    assert by_turn[4].frame_index == 34 and not by_turn[4].dropped


# The same hitch is reported as one gap from 900 to 1800 ms, which at 30 fps is 26 missing frames.
def test_frame_drops_are_reported_with_their_span_and_size():
    stamps = [i * 1000 / 30 for i in range(28)] + [1800 + i * 1000 / 30 for i in range(20)]
    gaps = find_frame_drops(stamps, fps=30)
    assert len(gaps) == 1
    assert gaps[0].start_ms == pytest.approx(900)
    assert gaps[0].end_ms == pytest.approx(1800)
    assert gaps[0].missing_frames == 26


# An evenly spaced recording has no drops (jitter under 1.5 frame intervals isn't a drop).
def test_steady_recording_has_no_frame_drops():
    stamps = [i * 1000 / 60 + (3 if i % 2 else 0) for i in range(120)]
    assert find_frame_drops(stamps, fps=60) == []


# End to end on a real (synthetic-content) mp4: 30 fps for 2 s gives turns 0..3 at 0, 500, 1000, 1500 ms, each a
# 720x1280 RGB frame that is the right source frame (grey level 2*index: 0, 30, 60, 90).
@pytest.mark.parametrize('fps', [30, 60])
def test_reading_a_video_yields_2hz_normalized_frames(tmp_path, fps):
    path = tmp_path / f'clip{fps}.mp4'
    write_video(path, fps, 2)
    ticks = list(read_ticks(path))
    assert [t.turn for t in ticks] == [0, 1, 2, 3]
    assert [t.timestamp_ms for t in ticks] == [0, 500, 1000, 1500]
    for t in ticks:
        assert t.image.shape == (1280, 720, 3)
        frame_index = t.turn * fps // 2
        centre = t.image[t.content.y + t.content.h // 2, t.content.x + t.content.w // 2]
        assert abs(int(centre[0]) - 2 * frame_index) <= 4

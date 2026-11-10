"""
Prototype: read the top HUD of GO Battle League videos (both Pokémon's names and CP, Pokémon left, shields left).

Usage, from anywhere: python3 -I tools/gbl-reader-prototype/read_battle.py [videos or folders...] [--frames]
With no arguments it reads every video in the repo's gitignored Screenrecordings folder.
--frames prints every frame's reading, not just the timeline.

Needs numpy, Pillow, ffmpeg and the tesseract CLI. Videos stay local: never commit them or frames from them.
"""
import json, os, re, subprocess, sys, tempfile, unicodedata
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_DIR = ROOT / 'pokemon-optimizer-backend/src/main/python/Screenrecordings'
GAMEMASTER = ROOT / 'pokemon-optimizer-backend/src/main/resources/gamemaster.json'
FPS = 2


# --- species names ------------------------------------------------------------------------------------
def norm(text):
    text = unicodedata.normalize('NFD', text)
    return re.sub(r'[^a-z0-9]', '', ''.join(c for c in text if not unicodedata.combining(c)).lower())


gm = json.load(open(GAMEMASTER))
NAMES = {}  # normalised on-screen name -> species name
for p in gm['pokemon']:
    name = p['speciesName']
    m = re.match(r'(.+?) \((.+)\)$', name)
    if m and 'Mega' in m.group(2):  # the battle HUD says "Mega Altaria", the gamemaster "Altaria (Mega)"
        NAMES.setdefault(norm('Mega ' + m.group(1)), name)
    base = m.group(1) if m else name  # forms show without the form: "Stunfisk", not "Stunfisk (Galarian)"
    NAMES.setdefault(norm(base), base)


def species_for(text):
    """The species name the OCR'd text is (or starts with: OCR adds junk), longest match; None for a nickname."""
    t = norm(text)
    best = None
    for key, name in NAMES.items():
        if len(key) >= 3 and t.startswith(key) and (best is None or len(key) > len(best[0])):
            best = (key, name)
    return best[1] if best else None


# --- reading the HUD ------------------------------------------------------------------------------------
def words_in(img):
    """Tesseract's words with their boxes: (x, y, w, h, text), sparse-text mode so the two panels read separately."""
    with tempfile.NamedTemporaryFile(suffix='.png', delete=False) as f:
        img.convert('L').save(f.name)
        out = subprocess.run(['tesseract', f.name, '-', '--psm', '11', 'tsv'], capture_output=True, text=True).stdout
    os.unlink(f.name)
    words = []
    for line in out.splitlines()[1:]:
        cols = line.split('\t')
        if len(cols) == 12 and cols[11].strip() and float(cols[10]) > 0:
            words.append((int(cols[6]), int(cols[7]), int(cols[8]), int(cols[9]), cols[11].strip()))
    return words


def count_runs(cols, min_width):
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


def read_side(a, words, left_side):
    """
    One side's panel, found from its "CP" label: the CP is the number after it, the name is the rest of that line,
    and the Poké Balls and shields are the row just below. None if this side has no CP label (no HUD on screen).
    """
    h, w, _ = a.shape
    half = [x for x in words if ((x[0] + x[2] / 2) < w / 2) == left_side]
    labels = [x for x in half if re.fullmatch(r'[CG]P\d*', x[4])]
    if not labels:
        return None
    label = min(labels, key=lambda x: x[1])  # the HUD is the topmost CP
    cy, lh = label[1] + label[3] / 2, label[3]
    line = sorted((x for x in half if abs(x[1] + x[3] / 2 - cy) < 0.7 * lh), key=lambda x: x[0])
    cp = None
    digits = re.sub(r'\D', '', label[4])
    if digits:
        cp = int(digits)
    else:
        after = [x for x in line if x[0] > label[0] and re.fullmatch(r'\d{2,5}', x[4])]
        if after:
            cp = int(after[0][4])
    name = ' '.join(x[4] for x in line if x is not label and not re.fullmatch(r'\d{2,5}', x[4]))
    x0 = max(0, min(x[0] for x in line) - int(0.02 * w))
    x1 = min(w, max(x[0] + x[2] for x in line) + int(0.02 * w))
    y0, y1 = int(label[1] + 1.4 * lh), int(label[1] + 3.6 * lh)
    icons = a[y0:y1, x0:x1].astype(int)
    r, g, b = icons[..., 0], icons[..., 1], icons[..., 2]
    red = (r > 190) & (g < 90) & (b < 90)  # a Poké Ball: a Pokémon still to play
    pink = (r > 170) & (b > 170) & (g < r - 25)  # a shield still to use
    icon_w = max(3, int(0.6 * lh))
    return {
        'name': name,
        'species': species_for(name),
        'cp': cp,
        'balls': count_runs(red.sum(axis=0) > 2, icon_w),
        'shields': count_runs(pink.sum(axis=0) > 2, icon_w),
    }


def read_frame(a):
    """Both sides of the HUD, or None when it isn't on screen (intros, charged-move animations)."""
    h = a.shape[0]
    words = words_in(Image.fromarray(a[: int(0.35 * h)]))
    left, right = read_side(a, words, True), read_side(a, words, False)
    return (left, right) if left and right else None


# --- videos ----------------------------------------------------------------------------------------------
def frames_of(video, out_dir):
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', str(video), '-vf', f'fps={FPS}', f'{out_dir}/%05d.png'],
                   check=True, stdin=subprocess.DEVNULL)
    return sorted(Path(out_dir).glob('*.png'))


def stamp(i):
    s = i / FPS
    return f'{int(s // 60)}:{s % 60:04.1f}'


def summary(side):
    return f"{side['species'] or '?' + side['name']!s:<18} CP {side['cp'] or '?':<5} balls {side['balls']} shields {side['shields']}"


def read_video(video, show_frames):
    tally = Counter()
    timeline = []  # (first frame, last frame, left species, right species)
    with tempfile.TemporaryDirectory() as tmp:
        frames = frames_of(video, tmp)
        for i, path in enumerate(frames):
            a = np.asarray(Image.open(path).convert('RGB'))
            sides = read_frame(a)
            if not sides:
                tally['no HUD'] += 1
                if show_frames:
                    print(f'  {stamp(i)}  no HUD')
                continue
            left, right = sides
            tally['HUD'] += 1
            for side in (left, right):
                tally['name matched' if side['species'] else 'name unmatched'] += 1
                tally['cp read' if side['cp'] else 'cp unread'] += 1
            if show_frames:
                print(f"  {stamp(i)}  {summary(left)}  |  {summary(right)}")
            # a name OCR misses for a frame keeps the side's last reading: Pokémon don't flicker in and out
            known = tuple(timeline[-1][2:]) if timeline else ('?', '?')
            pair = (left['species'] or known[0], right['species'] or known[1])
            if timeline and tuple(timeline[-1][2:]) == pair:
                timeline[-1][1] = i
            else:
                timeline.append([i, i, *pair])
    return len(frames), tally, timeline


def main():
    args = [x for x in sys.argv[1:] if not x.startswith('--')]
    show_frames = '--frames' in sys.argv
    paths = [Path(x) for x in args] or [DEFAULT_DIR]
    videos = []
    for p in paths:
        videos += sorted(q for q in p.rglob('*') if q.suffix in ('.webm', '.mp4', '.mkv', '.mov')) if p.is_dir() else [p]
    if not videos:
        sys.exit(f'No videos found in {", ".join(map(str, paths))}')
    total = Counter()
    for video in videos:
        print(f'\n{video.parent.name}/{video.name}')
        n, tally, timeline = read_video(video, show_frames)
        total += tally
        for first, last, l, r in timeline:
            print(f'  {stamp(first)}–{stamp(last)}  {l} vs {r}')
        print(f'  {n} frames: {dict(tally)}')
    print(f'\nAll videos: {dict(total)}')


main()

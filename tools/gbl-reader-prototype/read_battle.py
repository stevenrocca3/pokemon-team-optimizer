"""
Prototype: read the top HUD of GO Battle League videos (both Pokémon's names and CP, Pokémon left, shields left).

Usage, from anywhere: python3 -I tools/gbl-reader-prototype/read_battle.py [videos or folders...] [--frames]
With no arguments it reads every video in the repo's gitignored Screenrecordings folder.
--frames prints every frame's reading, not just the timeline. Readings are saved next to each video
(VIDEO.readings.json) so later runs skip the OCR; --reread does it again.

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


LEAGUE_CAPS = [(1500, 'Great League'), (2500, 'Ultra League'), (10**6, 'Master League')]


def read_raw(video):
    """Every frame's HUD reading, or None where there's no HUD. OCR is slow, so this is saved next to the video."""
    cache = video.with_name(video.stem + '.readings.json')
    if cache.exists() and '--reread' not in sys.argv:
        return json.loads(cache.read_text())
    readings = []
    with tempfile.TemporaryDirectory() as tmp:
        for path in frames_of(video, tmp):
            sides = read_frame(np.asarray(Image.open(path).convert('RGB')))
            readings.append(list(sides) if sides else None)
    cache.write_text(json.dumps(readings))
    return readings


def median(values):
    """The middle value; with an even count the lower one, so a real drop (a shield used) shows from its first frame."""
    s = sorted(values)
    return s[(len(s) - 1) // 2]


def clean(readings):
    """
    Fixes what OCR gets wrong in single frames, using what can't change mid-battle:
    - a side's Pokémon doesn't flicker: a name OCR missed keeps the last one read;
    - a Pokémon's CP is fixed: each Pokémon gets the CP read most often for it, and any other reading is replaced;
    - Poké Balls and shields only go down: each count is the median of it and its neighbours (two HUD frames each
      way), which drops one-frame misreads; a frame showing no balls at all had its icons hidden and doesn't vote.
      A rise that lasts is a new battle (Shorts cut between battles).
    Returns the HUD frames as (frame index, battle number, left, right) and a tally of what was fixed.
    """
    fixes = Counter()
    hud = [(i, [dict(s) for s in r]) for i, r in enumerate(readings) if r]
    for side in (0, 1):
        last = None
        for _, sides in hud:
            if sides[side]['species']:
                last = sides[side]['species']
            elif last:
                sides[side]['species'] = last
                fixes['name carried over'] += 1
        cps = {}
        for _, sides in hud:
            if sides[side]['species'] and sides[side]['cp']:
                cps.setdefault(sides[side]['species'], Counter())[sides[side]['cp']] += 1
        for _, sides in hud:
            counts = cps.get(sides[side]['species'])
            if counts:
                cp = counts.most_common(1)[0][0]
                if sides[side]['cp'] != cp:
                    fixes['CP corrected'] += 1
                    sides[side]['cp'] = cp
        # no balls can't be right while the HUD shows (the battle ends then): the icon row was hidden by an effect,
        # so neither count from that frame is trusted
        seen = [sides[side]['balls'] > 0 for _, sides in hud]
        for key in ('balls', 'shields'):
            raw = [sides[side][key] if ok else None for (_, sides), ok in zip(hud, seen)]
            previous = None
            for k, (_, sides) in enumerate(hud):
                near = [v for v in raw[max(0, k - 2): k + 3] if v is not None]
                smooth = median(near) if near else previous
                if smooth is None:
                    smooth = sides[side][key]
                if smooth != sides[side][key]:
                    fixes[f'{key} smoothed'] += 1
                sides[side][key] = previous = smooth
    out, battle = [], 1
    for k, (i, sides) in enumerate(hud):
        if k and any(sides[s][key] > hud[k - 1][1][s][key] for s in (0, 1) for key in ('balls', 'shields')):
            battle += 1
        out.append((i, battle, sides[0], sides[1]))
    return out, fixes


def league_of(frames):
    top = median([max(l['cp'] or 0, r['cp'] or 0) for _, _, l, r in frames])
    return next(name for cap, name in LEAGUE_CAPS if top <= cap)


def describe(first, last):
    """'Serperior CP 1498, 3 left, shields 2→1' for one side over a stretch of the timeline."""
    def count(key, word):
        a, b = first[key], last[key]
        return f'{word} {a}' if a == b else f'{word} {a}→{b}'
    return f"{first['species'] or '?'} CP {first['cp'] or '?'} ({count('balls', 'left')}, {count('shields', 'shields')})"


def timeline_of(frames):
    """Stretches where the same two Pokémon are out, in one battle."""
    stretches = []
    for i, battle, l, r in frames:
        key = (battle, l['species'], r['species'])
        if stretches and stretches[-1]['key'] == key:
            stretches[-1].update(last=i, end=(l, r))
        else:
            stretches.append({'key': key, 'first': i, 'last': i, 'start': (l, r), 'end': (l, r)})
    return stretches


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
        readings = read_raw(video)
        frames, fixes = clean(readings)
        print(f'\n{video.parent.name}/{video.name}: {len(frames)} of {len(readings)} frames show the HUD')
        if not frames:
            continue
        print(f'  {league_of(frames)}')
        if show_frames:
            for i, battle, l, r in frames:
                print(f'    {stamp(i)}  battle {battle}  {describe(l, l)}  |  {describe(r, r)}')
        battle = 0
        for s in timeline_of(frames):
            if s['key'][0] != battle:
                battle = s['key'][0]
                print(f'  Battle {battle}')
            (l0, r0), (l1, r1) = s['start'], s['end']
            print(f"    {stamp(s['first'])}–{stamp(s['last'])}  {describe(l0, l1)}  vs  {describe(r0, r1)}")
        print(f'  Fixed: {dict(fixes) or "nothing"}')
        total += fixes
    print(f'\nAll videos, fixed: {dict(total)}')


main()

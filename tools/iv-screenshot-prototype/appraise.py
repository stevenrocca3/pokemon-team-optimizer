"""Prototype: read IVs from Pokémon GO appraisal screenshots, then check them against the CP and HP on screen.

Usage, from anywhere: python3 -I tools/iv-screenshot-prototype/appraise.py [screenshots dir] [gamemaster.json] [cp.ts]
Every argument is optional: the defaults are this repo's screenshots folder, gamemaster and CP table.
"""
import json, math, re, subprocess, sys, tempfile, os
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
DEFAULTS = [
    ROOT / 'pokemon-optimizer-backend/src/main/python/Screenshots',
    ROOT / 'pokemon-optimizer-backend/src/main/resources/gamemaster.json',
    ROOT / 'pokemon-optimizer-frontend/src/lib/cp.ts',
]
args = [Path(a) for a in sys.argv[1:4]]
shots, gm_path, cp_ts = args + DEFAULTS[len(args):]
for path in (shots, gm_path, cp_ts):
    if not path.exists():
        sys.exit(f'Not found: {path}\nUsage: python3 -I appraise.py [screenshots dir] [gamemaster.json] [cp.ts]')

# --- data: base stats and CP multipliers -------------------------------------------------------------
gm = json.load(open(gm_path))
species = {}
for p in gm['pokemon']:
    b = p['baseStats']
    species.setdefault((b['atk'], b['def'], b['hp']), []).append(p['speciesName'])
src = open(cp_ts).read()
cpm = [float(x) for x in re.findall(r'0\.\d+', src[src.index('['):src.index(']')])]  # levels 1, 1.5 … 51
levels = [1 + i / 2 for i in range(len(cpm))]


def cp_of(base, iv, m):
    a, d, s = (base[i] + iv[i] for i in range(3))
    return max(10, math.floor(a * math.sqrt(d) * math.sqrt(s) * m * m / 10))


def hp_of(base, iv, m):
    return max(10, math.floor((base[2] + iv[2]) * m))


# --- bars ----------------------------------------------------------------------------------------------
def kind(px):
    r, g, b = int(px[0]), int(px[1]), int(px[2])
    if abs(r - g) < 10 and abs(g - b) < 10 and 195 < r < 238:
        return 'empty'
    if r > 215 and 130 < g < 200 and b < 120:
        return 'fill'          # orange: partly filled
    if r > 195 and g < 150 and b < 150:
        return 'fill'          # red-pink: a full bar
    return None


def white(px):
    return min(int(px[0]), int(px[1]), int(px[2])) > 238


def bar_span(a, y):
    """(start, end) of an appraisal bar on row y: one span of bar colours, white card on both sides, a third of
    the screen wide. None for anything else, like the red background behind the card."""
    h, w, _ = a.shape
    row = a[y]
    # runs of bar colours, bridging the short white gaps between a bar's three segments
    runs, start, last, gap = [], None, None, 0
    for x in range(int(w * 0.02), int(w * 0.65)):
        if kind(row[x]):
            if start is None:
                start = x
            last, gap = x, 0
        elif start is not None:
            gap += 1
            if gap > 20:   # blended edge pixels in a segment gap are neither bar colour nor white
                runs.append((start, last))
                start, gap = None, 0
    if start is not None:
        runs.append((start, last))
    for start, end in runs:
        if 0.30 * w <= end - start <= 0.40 * w and white(row[start - 8]) and white(row[min(w - 1, end + 8)]):
            return start, end
    return None


def read_bars(a):
    h, w, _ = a.shape
    x0, x1 = int(w * 0.05), int(w * 0.55)
    rows = [y for y in range(int(h * 0.6), int(h * 0.95)) if bar_span(a, y)]
    # group consecutive rows into bars, keep the middle row of each
    bars, start = [], None
    for i, y in enumerate(rows):
        if start is None:
            start = y
        if i == len(rows) - 1 or rows[i + 1] != y + 1:
            if y - start >= 8:
                bars.append((start + y) // 2)
            start = None
    if len(bars) != 3:
        return None, bars
    ivs = []
    for y in bars:
        start, end = bar_span(a, y)
        kinds = [kind(p) for p in a[y, start:end + 1]]
        filled = sum(k == 'fill' for k in kinds)
        total = sum(k is not None for k in kinds)
        ivs.append(round(15 * filled / total))
    return ivs, bars


# --- OCR -----------------------------------------------------------------------------------------------
def ocr(img, box, psm=7, whitelist=None, white_text=False):
    w, h = img.size
    crop = img.crop((int(box[0] * w), int(box[1] * h), int(box[2] * w), int(box[3] * h)))
    if white_text:
        # the CP is white on any background: keep only near-white pixels, as black text on white
        arr = np.asarray(crop.convert('RGB')).astype(int)
        crop = Image.fromarray(np.where(arr.min(axis=2) > white_text, 0, 255).astype('uint8'))
    else:
        crop = crop.convert('L')
    with tempfile.NamedTemporaryFile(suffix='.png', delete=False) as f:
        crop.save(f.name)
        args = ['tesseract', f.name, '-', '--psm', str(psm)]
        if whitelist:
            args += ['-c', f'tessedit_char_whitelist={whitelist}']
        out = subprocess.run(args, capture_output=True, text=True).stdout
    os.unlink(f.name)
    return out.strip()


def hp_bar_row(a):
    """The first row of the green HP bar under the name, or None."""
    h, w, _ = a.shape
    for y in range(int(0.3 * h), int(0.6 * h)):
        row = a[y, int(0.2 * w):int(0.8 * w)].astype(int)
        green = ((row[:, 1] > 180) & (row[:, 1] - row[:, 0] > 60) & (row[:, 2] < row[:, 1])).sum()
        if green > 0.3 * w:
            return y
    return None


def read_text(img):
    """Candidate CPs (each OCR'd a different way: the check below picks the one that fits), max HP and name."""
    cps = []
    for box, prep in [(b, p) for b in ((0.30, 0.045, 0.70, 0.105), (0.25, 0.04, 0.75, 0.105)) for p in (False, 200, 225)]:
        text = ocr(img, box, whitelist='CP0123456789', white_text=prep).replace('CP', '')
        m = re.search(r'(\d{2,4})\s*$', text)
        if m and int(m.group(1)) not in cps:
            cps.append(int(m.group(1)))
    # the name sits just above the green HP bar and "133 / 133 HP" just below it, wherever the card is on screen;
    # offsets are in screen widths because the text scales with the width
    w, h = img.size
    bar = hp_bar_row(np.asarray(img))
    if bar is None:
        return cps, None, ''
    top = lambda k: (bar + k * w) / h
    hp_text = ocr(img, (0.30, top(0.004), 0.70, top(0.085)), whitelist='0123456789/HP ')
    name = ocr(img, (0.15, top(-0.115), 0.75, top(-0.004)))
    hp = re.search(r'(\d+)\s*/\s*(\d+)', hp_text)
    return cps, (int(hp.group(2)) if hp else None), name


# --- check -----------------------------------------------------------------------------------------------
def fits(name, cp, hp, ivs):
    """Species and levels where these exact IVs give the CP and max HP on screen."""
    out = []
    for base, names in species.items():
        if name and not any(n.lower().startswith(name.lower()[:4]) for n in names):
            continue
        for lvl, m in zip(levels, cpm):
            if cp_of(base, ivs, m) == cp and hp_of(base, ivs, m) == hp:
                shown = next((n for n in names if name and n.lower().startswith(name.lower()[:4])), names[0])
                out.append((shown, lvl))
    return out


results = []
sizes = {}
for f in sorted(shots.iterdir()):
    if f.name.startswith('.'):
        continue
    try:
        img = Image.open(f).convert('RGB')
    except Exception:
        results.append((f.name, 'not an image'))
        continue
    if img.width < 600:
        sizes[f.name] = 'under 600 px wide'
        results.append((f.name, 'too small to read'))
        continue
    a = np.asarray(img)
    sizes[f.name] = f'{img.width}x{img.height} {f.suffix.lower().lstrip(".")}'
    ivs, bars = read_bars(a)
    cps, hp, name = read_text(img)
    if ivs is None:
        results.append((f.name, f'bars not found ({len(bars)} row groups)', cps, hp, name))
        continue
    if not cps or hp is None:
        results.append((f.name, 'ocr failed', ivs, cps, hp, name))
        continue
    found = [(cp, fits(name, cp, hp, ivs)) for cp in cps]   # the species on screen only: some share base stats
    found = [(cp, m) for cp, m in found if m]
    if found:
        results.append((f.name, 'consistent', ivs, found[0][0], hp, name, found[0][1][:2]))
    else:
        results.append((f.name, 'INCONSISTENT', ivs, cps, hp, name))

for r in results:
    print(sizes.get(r[0], '?'), *r)

# per screen size and format: a layout or compression that breaks shows up here instead of hiding in the total
from collections import Counter, defaultdict
groups = defaultdict(list)
for r in results:
    groups[sizes.get(r[0], 'unreadable')].append(r[1])
print()
for size, outcomes in sorted(groups.items(), key=lambda g: -len(g[1])):
    ok = outcomes.count('consistent')
    print(f'{size:22} {ok:3}/{len(outcomes):<3} verified   ', dict(Counter(o for o in outcomes if o != 'consistent')))
print()
print(Counter(r[1] for r in results))

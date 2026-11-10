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


def median(values):
    """The middle value; with an even count the lower one, so a real drop (a shield used) shows from its first frame."""
    s = sorted(values)
    return s[(len(s) - 1) // 2]


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


def hp_bars(a):
    """
    The in-scene HP bars as (x0, x1, y, hp from 0 to 1), top of the screen first.
    A bar is framed by two light border lines, top and bottom, ~0.29 of the screen wide and ~0.026 apart, with the
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
            # the two lines cover the same span (something light can run into one end, so it's overlap, not equal ends)
            overlap = min(x1, u1) - max(x0, u0)
            if gap >= 0.018 * w and overlap >= 0.95 * min(x1 - x0, u1 - u0):
                x0, x1 = max(x0, u0), min(x1, u1)
                if min(x0, u0) <= 2 or max(x1, u1) >= w - 2:
                    break  # cut off by the screen's edge, so its fill can't be measured
                mid = band[(y + y2) // 2, x0:x1]
                m_mx, m_mn = mid.max(axis=1), mid.min(axis=1)
                colour = (m_mx > 150) & (m_mx - m_mn > 80) & (m_mn < 120)  # not the (bluish at night) border
                track = m_mx < 70
                inside = np.flatnonzero(colour | track)
                if len(inside) < 0.8 * (x1 - x0):
                    break  # mostly neither fill nor track: not a bar after all
                last = inside[-1] + 1
                # the fill starts just inside the frame (dark outline pixels can come first); none there means 0 HP
                m_r, m_g, m_b = mid[:, 0], mid[:, 1], mid[:, 2]
                orange = (m_r > 200) & (m_g > 100) & (m_g < 190) & (m_b < 90)  # the hit flash or just-lost HP, never fill
                solid = colour & ~orange
                # a run of 4, so a blended pixel at the frame's edge isn't taken for the start
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
    return sorted(bars, key=lambda b: b[2])


def read_frame(a):
    """Both sides of the HUD, or None when it isn't on screen (intros, charged-move animations)."""
    h = a.shape[0]
    words = words_in(Image.fromarray(a[: int(0.35 * h)]))
    left, right = read_side(a, words, True), read_side(a, words, False)
    if not (left and right):
        return None
    # your Pokémon is nearer the camera, so its bar is the lower one; one bar alone is sorted out later from HP
    bars = hp_bars(a)
    left['hp'] = right['hp'] = left['lone_hp'] = None
    if len(bars) >= 2:
        right['hp'], left['hp'] = round(bars[0][3], 3), round(bars[-1][3], 3)
    elif bars:
        left['lone_hp'] = round(bars[0][3], 3)
    return left, right


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
        readings = json.loads(cache.read_text())
        if all('hp' in r[0] for r in readings if r):  # saved before HP was read: read again
            return readings
    readings = []
    with tempfile.TemporaryDirectory() as tmp:
        for path in frames_of(video, tmp):
            sides = read_frame(np.asarray(Image.open(path).convert('RGB')))
            readings.append(list(sides) if sides else None)
    cache.write_text(json.dumps(readings))
    return readings


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
    # HP: a lone bar goes to the side whose Pokémon's HP it's closest to without being higher (HP never goes back
    # up; a Pokémon not seen yet is at full). An empty bar only fits a side that loses a Poké Ball soon after.
    last_hp = {}  # (side, species) -> HP
    for k, (_, sides) in enumerate(hud):
        lone = sides[0].pop('lone_hp', None)
        if lone is not None and sides[0]['hp'] is None:
            def faints(s):
                return any(hud[j][1][s]['balls'] < sides[s]['balls'] for j in range(k + 1, min(k + 7, len(hud))))
            fits = [s for s in (0, 1) if lone <= last_hp.get((s, sides[s]['species']), 1.0) + 0.05
                    and (lone > 0.02 or faints(s))]
            if fits:
                side = min(fits, key=lambda s: abs(last_hp.get((s, sides[s]['species']), 1.0) - lone))
                sides[side]['hp'] = lone
                fixes['lone HP bar placed'] += 1
        for s in (0, 1):
            if sides[s]['hp'] is not None:
                last_hp[(s, sides[s]['species'])] = sides[s]['hp']
    # each Pokémon's HP: smoothed over its neighbouring readings, then only ever going down
    for s in (0, 1):
        by_species = {}
        for k, (_, sides) in enumerate(hud):
            if sides[s]['hp'] is not None:
                by_species.setdefault(sides[s]['species'], []).append(k)
        for ks in by_species.values():
            # a charged move lands while the HUD is hidden, so HP really jumps across a gap: smooth only within stretches
            stretches = [[ks[0]]]
            for prev, k in zip(ks, ks[1:]):
                if hud[k][0] - hud[prev][0] > 4:
                    stretches.append([])
                stretches[-1].append(k)
            lowest = 1.0
            for stretch in stretches:
                raw = [hud[k][1][s]['hp'] for k in stretch]
                for j, k in enumerate(stretch):
                    v = min(median(raw[max(0, j - 2): j + 3]), lowest)
                    if abs(v - raw[j]) > 0.02:
                        fixes['HP smoothed'] += 1
                    hud[k][1][s]['hp'] = lowest = v
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
    hp = ''
    if first.get('hp') is not None and last.get('hp') is not None:
        a, b = round(100 * first['hp']), round(100 * last['hp'])
        hp = f', HP {a}%' if a == b else f', HP {a}%→{b}%'
    return f"{first['species'] or '?'} CP {first['cp'] or '?'} ({count('balls', 'left')}, {count('shields', 'shields')}{hp})"


def timeline_of(frames):
    """Stretches where the same two Pokémon are out, in one battle."""
    stretches = []
    for i, battle, l, r in frames:
        key = (battle, l['species'], r['species'])
        if stretches and stretches[-1]['key'] == key:
            st = stretches[-1]
            st.update(last=i, end=(l, r))
            for side, cur in enumerate((l, r)):  # HP from the first and last frames that showed it
                if cur.get('hp') is not None:
                    if st['hp_start'][side] is None:
                        st['hp_start'][side] = cur['hp']
                    st['hp_end'][side] = cur['hp']
        else:
            stretches.append({'key': key, 'first': i, 'last': i, 'start': (l, r), 'end': (l, r),
                              'hp_start': [l.get('hp'), r.get('hp')], 'hp_end': [l.get('hp'), r.get('hp')]})
    for st in stretches:
        st['start'] = tuple(dict(side, hp=st['hp_start'][k]) for k, side in enumerate(st['start']))
        st['end'] = tuple(dict(side, hp=st['hp_end'][k]) for k, side in enumerate(st['end']))
    return stretches


def main():
    args = [x for x in sys.argv[1:] if not x.startswith('--')]
    show_frames = '--frames' in sys.argv
    paths = [Path(x) for x in args] or [DEFAULT_DIR]
    videos = []
    for p in paths:
        videos += sorted(q for q in p.rglob('*') if q.suffix.lower() in ('.webm', '.mp4', '.mkv', '.mov')) if p.is_dir() else [p]
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


if __name__ == "__main__":
    main()

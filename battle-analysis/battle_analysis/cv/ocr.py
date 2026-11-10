"""Text reading with the Tesseract CLI (installed in the image; `brew install tesseract` locally)."""
import subprocess
from dataclasses import dataclass

import cv2
import numpy as np


@dataclass(frozen=True)
class Word:
    x: int
    y: int
    w: int
    h: int
    text: str

    @property
    def cx(self) -> float:
        return self.x + self.w / 2

    @property
    def cy(self) -> float:
        return self.y + self.h / 2


def _png(image_rgb: np.ndarray) -> bytes:
    # colour, not our own greyscale: Tesseract's conversion keeps the HUD's teal-on-grey names readable, ours doesn't
    ok, buf = cv2.imencode('.png', cv2.cvtColor(image_rgb, cv2.COLOR_RGB2BGR))
    if not ok:
        raise ValueError('could not encode image for OCR')
    return buf.tobytes()


def words(image_rgb: np.ndarray, psm: int = 11) -> list[Word]:
    """Tesseract's words with their boxes; sparse-text mode (11) by default, so separate panels read separately."""
    if image_rgb.size == 0:
        return []
    out = subprocess.run(['tesseract', 'stdin', '-', '--psm', str(psm), 'tsv'], input=_png(image_rgb),
                         capture_output=True, timeout=30).stdout.decode('utf-8', 'replace')
    found = []
    for line in out.splitlines()[1:]:
        cols = line.split('\t')
        if len(cols) == 12 and cols[11].strip() and float(cols[10]) >= 0:  # -1 marks layout rows, not words
            found.append(Word(int(cols[6]), int(cols[7]), int(cols[8]), int(cols[9]), cols[11].strip()))
    return found


def lines(image_rgb: np.ndarray, psm: int = 11) -> list[str]:
    """The words grouped into lines of text, top to bottom."""
    rows: list[list[Word]] = []
    for w in sorted(words(image_rgb, psm), key=lambda w: (w.cy, w.x)):
        if rows and abs(rows[-1][0].cy - w.cy) < 0.6 * max(w.h, rows[-1][0].h):
            rows[-1].append(w)
        else:
            rows.append([w])
    return [' '.join(w.text for w in sorted(r, key=lambda w: w.x)) for r in rows]

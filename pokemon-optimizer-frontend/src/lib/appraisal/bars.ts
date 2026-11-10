import type { Pixels } from './pixels'

type Kind = 'empty' | 'fill' | null

/** What an appraisal bar pixel is: grey when empty, orange when part-filled, red-pink when the bar is full. */
function kind(d: Uint8ClampedArray, i: number): Kind {
  const r = d[i]
  const g = d[i + 1]
  const b = d[i + 2]
  if (Math.abs(r - g) < 10 && Math.abs(g - b) < 10 && r > 195 && r < 238) return 'empty'
  if (r > 215 && g > 130 && g < 200 && b < 120) return 'fill'
  if (r > 195 && g < 150 && b < 150) return 'fill'
  return null
}

const isWhite = (d: Uint8ClampedArray, i: number) => d[i] > 238 && d[i + 1] > 238 && d[i + 2] > 238

/**
 * Where a bar runs on row y: one span of bar colours (bridging the short gaps between its three segments, whose edge
 * pixels blend into white), a third of the screen wide, with the white card on both sides. Anything else, like the red
 * background behind the card, is null.
 */
function barSpan(p: Pixels, y: number): [number, number] | null {
  const { width: w, data: d } = p
  const maxGap = Math.max(8, Math.round(0.017 * w))
  const at = (x: number) => (y * w + x) * 4
  let start = -1
  let last = -1
  let gap = 0
  const check = (s: number, e: number): [number, number] | null => {
    const span = e - s
    if (span < 0.3 * w || span > 0.4 * w) return null
    if (!isWhite(d, at(Math.max(0, s - 8))) || !isWhite(d, at(Math.min(w - 1, e + 8)))) return null
    return [s, e]
  }
  for (let x = Math.round(0.02 * w); x < Math.round(0.65 * w); x++) {
    if (kind(d, at(x))) {
      if (start < 0) start = x
      last = x
      gap = 0
    } else if (start >= 0 && ++gap > maxGap) {
      const found = check(start, last)
      if (found) return found
      start = -1
    }
  }
  return start >= 0 ? check(start, last) : null
}

/** The IVs shown by the three appraisal bars (attack, defense, HP), or null if this isn't an appraisal screen. */
export function readBars(p: Pixels): [number, number, number] | null {
  const minHeight = Math.max(4, Math.round(0.007 * p.width))
  const bars: number[] = []
  let first = -1
  for (let y = Math.round(0.6 * p.height); y <= Math.round(0.95 * p.height); y++) {
    const on = y < Math.round(0.95 * p.height) && barSpan(p, y) !== null
    if (on && first < 0) first = y
    if (!on && first >= 0) {
      if (y - first >= minHeight) bars.push(Math.floor((first + y - 1) / 2))
      first = -1
    }
  }
  if (bars.length !== 3) return null
  return bars.map((y) => {
    const [start, end] = barSpan(p, y)!
    let filled = 0
    let total = 0
    for (let x = start; x <= end; x++) {
      const k = kind(p.data, (y * p.width + x) * 4)
      if (k) total++
      if (k === 'fill') filled++
    }
    return Math.round((15 * filled) / total)
  }) as [number, number, number]
}

// Tests only: draws simplified appraisal screens in code. Not real screenshots (those can't be committed: they show
// where each Pokémon was caught). Accuracy on real ones is measured by appraisal-accuracy.test.ts, run locally.
import type { Pixels } from './pixels'

export type Rgb = [number, number, number]

export const WHITE: Rgb = [255, 255, 255]
export const RED_BACKGROUND: Rgb = [200, 70, 70]
export const ORANGE: Rgb = [244, 166, 76] // a partly filled bar
export const FULL_RED: Rgb = [229, 115, 115] // a full bar
export const EMPTY_GREY: Rgb = [226, 226, 228]
export const HP_GREEN: Rgb = [146, 233, 192]

export function blank(width: number, height: number, colour: Rgb = WHITE): Pixels {
  const data = new Uint8ClampedArray(width * height * 4)
  for (let i = 0; i < width * height; i++) data.set([...colour, 255], i * 4)
  return { width, height, data }
}

export function fillRect(p: Pixels, x0: number, y0: number, x1: number, y1: number, colour: Rgb) {
  for (let y = Math.max(0, y0); y < Math.min(p.height, y1); y++) {
    for (let x = Math.max(0, x0); x < Math.min(p.width, x1); x++) p.data.set(colour, (y * p.width + x) * 4)
  }
}

/**
 * An appraisal screen like an iPhone's at this size: red background, the white card, the green HP bar under the name,
 * and the three IV bars, each split into three segments by white gaps.
 */
export function appraisalScreen(ivs: [number, number, number], width = 1170, height = 2532): Pixels {
  const p = blank(width, height, RED_BACKGROUND)
  const s = width / 1170
  fillRect(p, Math.round(30 * s), Math.round(0.33 * height), Math.round(1140 * s), height, WHITE) // the card
  fillRect(p, Math.round(300 * s), Math.round(0.452 * height), Math.round(870 * s), Math.round(0.452 * height + 12 * s), HP_GREEN)
  fillRect(p, 0, Math.round(0.62 * height), width, Math.round(0.64 * height), RED_BACKGROUND) // a red band across: not a bar
  const left = Math.round(138 * s)
  const barWidth = Math.round(406 * s)
  const gap = Math.round(8 * s)
  ivs.forEach((iv, i) => {
    const top = Math.round((0.77 + i * 0.044) * height)
    const bottom = top + Math.round(22 * s)
    const filled = Math.round((barWidth * iv) / 15)
    fillRect(p, left, top, left + barWidth, bottom, EMPTY_GREY)
    fillRect(p, left, top, left + filled, bottom, iv === 15 ? FULL_RED : ORANGE)
    for (const k of [1, 2]) {
      const x = left + Math.round((barWidth * k) / 3)
      fillRect(p, x - gap / 2, top, x + gap / 2, bottom, WHITE)
    }
  })
  return p
}

import { crop, type Box, type Pixels } from './pixels'

/**
 * The first row of the green HP bar under the Pokémon's name, or null. The card sits higher or lower depending on
 * the phone's shape, so the name and HP are found from this bar rather than at fixed heights.
 */
export function hpBarRow(p: Pixels): number | null {
  const { width: w, height: h, data: d } = p
  for (let y = Math.round(0.3 * h); y < Math.round(0.6 * h); y++) {
    let green = 0
    for (let x = Math.round(0.2 * w); x < Math.round(0.8 * w); x++) {
      const i = (y * w + x) * 4
      if (d[i + 1] > 180 && d[i + 1] - d[i] > 60 && d[i + 2] < d[i + 1]) green++
    }
    if (green > 0.3 * w) return y
  }
  return null
}

/** Where the name (just above the HP bar) and "268 / 268 HP" (just below) are. Offsets scale with the width, like the text. */
export function textBoxes(width: number, _height: number, hpBar: number): { name: Box; hp: Box } {
  return {
    name: { x: 0.15 * width, y: hpBar - 0.115 * width, width: 0.6 * width, height: 0.111 * width },
    hp: { x: 0.3 * width, y: hpBar + 0.004 * width, width: 0.4 * width, height: 0.081 * width },
  }
}

/** Two crops of the CP at the top of the screen, tight and wide: each suits different phones. */
export function cpBoxes(width: number, height: number): Box[] {
  return [
    { x: 0.3 * width, y: 0.045 * height, width: 0.4 * width, height: 0.06 * height },
    { x: 0.25 * width, y: 0.04 * height, width: 0.5 * width, height: 0.065 * height },
  ]
}

/**
 * The CP is white text on whatever is behind the Pokémon. Keeping only near-white pixels, as black on white, makes it
 * readable on bright backgrounds too. `threshold` is the lowest channel value that counts as white.
 */
export function whiteTextMask(p: Pixels, box: Box, threshold: number): Pixels {
  const out = crop(p, box)
  for (let i = 0; i < out.data.length; i += 4) {
    const v = Math.min(out.data[i], out.data[i + 1], out.data[i + 2]) > threshold ? 0 : 255
    out.data[i] = out.data[i + 1] = out.data[i + 2] = v
    out.data[i + 3] = 255
  }
  return out
}

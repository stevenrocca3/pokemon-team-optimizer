/**
 * Game sprites draw each Pokémon at its in-game size inside a fixed canvas, so a small one (Charjabug) is a speck
 * next to a big one. These find the visible part of a sprite and the transform that makes it fill its box.
 */
export interface Bounds { x: number; y: number; width: number; height: number }

/** Below this alpha a pixel counts as transparent (sprites have faint anti-aliased fringes). */
const VISIBLE_ALPHA = 16
const MAX_SCALE = 4

/** The box around the visible pixels of an RGBA image, or null if none are visible. */
export function opaqueBounds(data: Uint8ClampedArray, width: number, height: number): Bounds | null {
  let left = width, top = height, right = -1, bottom = -1
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (data[(y * width + x) * 4 + 3] >= VISIBLE_ALPHA) {
        if (x < left) left = x
        if (x > right) right = x
        if (y < top) top = y
        if (y > bottom) bottom = y
      }
    }
  }
  return right < 0 ? null : { x: left, y: top, width: right - left + 1, height: bottom - top + 1 }
}

/**
 * For `transform-origin: top left; transform: translate(x%, y%) scale(scale)` on a square canvas of `size`:
 * the scale that makes the visible part's longer side fill it (at most 4×), and the shift that centres it.
 */
export function fitTransform(bounds: Bounds, size: number): { scale: number; x: number; y: number } {
  const scale = Math.min(MAX_SCALE, size / Math.max(bounds.width, bounds.height))
  const centreX = bounds.x + bounds.width / 2
  const centreY = bounds.y + bounds.height / 2
  return {   // `+ 0` turns -0 into 0
    scale,
    x: ((size / 2 - scale * centreX) / size) * 100 + 0,
    y: ((size / 2 - scale * centreY) / size) * 100 + 0,
  }
}

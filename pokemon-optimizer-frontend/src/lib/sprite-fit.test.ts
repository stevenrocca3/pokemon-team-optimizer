import { describe, expect, it } from 'vitest'
import { fitTransform, opaqueBounds } from './sprite-fit'

/** A width×height RGBA image, transparent except the given [x, y] pixels. */
function image(width: number, height: number, opaque: [number, number][]) {
  const data = new Uint8ClampedArray(width * height * 4)
  for (const [x, y] of opaque) data[(y * width + x) * 4 + 3] = 255
  return data
}

describe('opaqueBounds', () => {
  it('finds the box around the visible pixels', () => {
    expect(opaqueBounds(image(10, 10, [[3, 4], [6, 5], [4, 8]]), 10, 10)).toEqual({ x: 3, y: 4, width: 4, height: 5 })
  })

  it('ignores nearly transparent edge pixels', () => {
    const data = image(10, 10, [[5, 5]])
    data[(0 * 10 + 0) * 4 + 3] = 8
    expect(opaqueBounds(data, 10, 10)).toEqual({ x: 5, y: 5, width: 1, height: 1 })
  })

  it('is null for an empty image', () => {
    expect(opaqueBounds(image(4, 4, []), 4, 4)).toBeNull()
  })
})

describe('fitTransform', () => {
  it('scales a small sprite up so its longer side fills the box, centred', () => {
    // origin top left: a 24×12 Pokémon in the corner of a 96px canvas scales 4×, so its centre (12, 6) lands at (48, 24);
    // moving that to the middle (48, 48) is 0px and 24px, i.e. 0% and 25% of the canvas
    expect(fitTransform({ x: 0, y: 0, width: 24, height: 12 }, 96)).toEqual({ scale: 4, x: 0, y: 25 })
  })

  it('never scales more than 4×, so a tiny sprite stays recognisable', () => {
    expect(fitTransform({ x: 44, y: 44, width: 8, height: 8 }, 96).scale).toBe(4)
  })

  it('leaves a sprite that already fills the canvas alone', () => {
    expect(fitTransform({ x: 0, y: 0, width: 96, height: 96 }, 96)).toEqual({ scale: 1, x: 0, y: 0 })
  })
})

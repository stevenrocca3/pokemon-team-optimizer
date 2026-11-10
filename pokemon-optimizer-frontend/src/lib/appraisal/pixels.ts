/** RGBA pixels, laid out like the browser's ImageData, so a canvas's pixels can be passed straight in. */
export interface Pixels {
  width: number
  height: number
  data: Uint8ClampedArray
}

export interface Box {
  x: number
  y: number
  width: number
  height: number
}

/** The pixels inside `box`, clipped to the image. */
export function crop(p: Pixels, box: Box): Pixels {
  const x0 = Math.max(0, Math.round(box.x))
  const y0 = Math.max(0, Math.round(box.y))
  const x1 = Math.min(p.width, Math.round(box.x + box.width))
  const y1 = Math.min(p.height, Math.round(box.y + box.height))
  const width = Math.max(0, x1 - x0)
  const height = Math.max(0, y1 - y0)
  const data = new Uint8ClampedArray(width * height * 4)
  for (let y = 0; y < height; y++) {
    const from = ((y0 + y) * p.width + x0) * 4
    data.set(p.data.subarray(from, from + width * 4), y * width * 4)
  }
  return { width, height, data }
}

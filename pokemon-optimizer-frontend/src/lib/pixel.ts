import type { CSSProperties } from 'react'

/** Corners stepped like pixel art, `s` px per step, as a clip-path. */
export function pixelCorners(s: number): CSSProperties {
  const a = `${s}px`
  const b = `${2 * s}px`
  const W = (d: string) => `calc(100% - ${d})`
  return {
    clipPath: `polygon(${b} 0, ${W(b)} 0, ${W(b)} ${a}, ${W(a)} ${a}, ${W(a)} ${b}, 100% ${b}, 100% ${W(b)}, ${W(a)} ${W(b)}, ${W(a)} ${W(a)}, ${W(b)} ${W(a)}, ${W(b)} 100%, ${b} 100%, ${b} ${W(a)}, ${a} ${W(a)}, ${a} ${W(b)}, 0 ${W(b)}, 0 ${b}, ${a} ${b}, ${a} ${a}, ${b} ${a})`,
  }
}

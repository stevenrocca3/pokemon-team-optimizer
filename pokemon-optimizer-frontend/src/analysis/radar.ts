/**
 * The radar scan on /analyze: an arm sweeping round a scope while contacts light up as the analysis gets further.
 * Pure maths here; ScanVisualizer's canvas draws whatever these return. Angles are radians clockwise from 12 o'clock;
 * radii are shares of the scope's radius.
 */
export interface Blip {
  angle: number
  radius: number
}

const TAU = Math.PI * 2
export const SWEEP_MS = 2400
const MAX_BLIPS = 12

export const sweepAngle = (ms: number) => ((ms % SWEEP_MS) / SWEEP_MS) * TAU

/** a tiny seeded PRNG (mulberry32), so the contacts sit in the same places on every frame and every visit */
function seeded(seed: number) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

// spread round the scope by the golden angle, jittered, kept off the centre and the rim
const PLACES: Blip[] = Array.from({ length: MAX_BLIPS }, (_, i) => {
  const random = seeded(i + 1)
  return { angle: (i * 2.39996 + random() * 0.6) % TAU, radius: 0.2 + random() * 0.7 }
})

/** the contacts showing at this progress (0-100): one more every 1/12 of the way */
export function blips(progress: number): Blip[] {
  const share = Math.min(100, Math.max(0, progress)) / 100
  return PLACES.slice(0, Math.round(share * MAX_BLIPS))
}

/** how brightly a contact shows (1 to 0.15): full as the arm passes, fading over the following turn */
export function blipGlow(blipAngle: number, sweep: number): number {
  const since = (((sweep - blipAngle) % TAU) + TAU) % TAU
  return 1 - 0.85 * (since / TAU)
}

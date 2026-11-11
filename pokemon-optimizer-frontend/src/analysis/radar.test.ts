// Issue #105: the radar scan's maths (the canvas only draws what these return).
import { describe, expect, it } from 'vitest'
import { SWEEP_MS, blipGlow, blips, sweepAngle } from './radar'

describe('radar sweep', () => {
  // The sweep arm goes once round the scope every 2.4 seconds: a quarter turn at 0.6 s, back to the start at 2.4 s.
  it('turns once every 2.4 seconds', () => {
    expect(SWEEP_MS).toBe(2400)
    expect(sweepAngle(0)).toBeCloseTo(0)
    expect(sweepAngle(600)).toBeCloseTo(Math.PI / 2)
    expect(sweepAngle(1800)).toBeCloseTo((3 * Math.PI) / 2)
    expect(sweepAngle(2400)).toBeCloseTo(0)
  })
})

describe('radar blips', () => {
  // Contacts appear as the analysis gets further: none at 0%, 6 at 50%, all 12 at 100%. The same progress always
  // gives the same contacts in the same places (no jumping around between frames), and they all sit inside the
  // scope, clear of the centre.
  it('grow with progress, in fixed places inside the scope', () => {
    expect(blips(0)).toHaveLength(0)
    expect(blips(50)).toHaveLength(6)
    expect(blips(100)).toHaveLength(12)
    expect(blips(100)).toEqual(blips(100))
    expect(blips(100).slice(0, 6)).toEqual(blips(50))
    for (const b of blips(100)) {
      expect(b.radius).toBeGreaterThanOrEqual(0.2)
      expect(b.radius).toBeLessThanOrEqual(0.9)
      expect(b.angle).toBeGreaterThanOrEqual(0)
      expect(b.angle).toBeLessThan(2 * Math.PI)
    }
  })

  // A contact flares as the arm passes it and fades over the next turn: full glow just after the sweep, about half
  // way down after half a turn, and a faint 0.15 just before the arm comes round again.
  it('flare as the sweep passes, then fade', () => {
    expect(blipGlow(1, 1)).toBeCloseTo(1)
    expect(blipGlow(1, 1 + Math.PI)).toBeCloseTo(0.575)
    expect(blipGlow(1, 1 - 0.001)).toBeCloseTo(0.15, 2)
  })
})

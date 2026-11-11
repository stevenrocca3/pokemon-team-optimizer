import { describe, expect, it } from 'vitest'
import { ANALYZE_DEMO_MS, DEMO_SCORE, MISTAKE_AT, analyzeDemoFrame } from './analyze-demo'

describe('analyzeDemoFrame', () => {
  it('opens on the battle: both sides start full, then trade fast moves', () => {
    expect(analyzeDemoFrame(0)).toMatchObject({ phase: 'battle', youHp: 1, foeHp: 1, energy: 0 })
    const later = analyzeDemoFrame(2500)
    expect(later.phase).toBe('battle')
    if (later.phase !== 'battle') return
    expect(later.foeHp).toBeLessThan(1)
    expect(later.youHp).toBeLessThan(1)
    expect(later.energy).toBeGreaterThan(0)
  })

  it('has the opponent throw a charged move mid-battle, and you shield it', () => {
    expect(analyzeDemoFrame(2900)).toMatchObject({ phase: 'battle', foeThrow: true, shielding: false })
    expect(analyzeDemoFrame(3300)).toMatchObject({ phase: 'battle', foeThrow: true, shielding: true })
    expect(analyzeDemoFrame(4200)).toMatchObject({ phase: 'battle', foeThrow: false, shielding: false })
  })

  // GO shows each side's shields under its HP bar: two at the start, one gone once you shield
  it('spends one of your two shields when you shield', () => {
    expect(analyzeDemoFrame(2900)).toMatchObject({ phase: 'battle', shieldsLeft: 2 })
    expect(analyzeDemoFrame(3300)).toMatchObject({ phase: 'battle', shieldsLeft: 1 })
    expect(analyzeDemoFrame(4500)).toMatchObject({ phase: 'battle', shieldsLeft: 1 })
  })

  it('never lets energy or HP leave 0 to 1', () => {
    for (let ms = 0; ms < 5000; ms += 50) {
      const f = analyzeDemoFrame(ms)
      if (f.phase !== 'battle') continue
      for (const v of [f.youHp, f.foeHp, f.energy]) {
        expect(v).toBeGreaterThanOrEqual(0)
        expect(v).toBeLessThanOrEqual(1)
      }
    }
  })

  it('then draws the charts: the line first, the bars after', () => {
    expect(analyzeDemoFrame(5000)).toMatchObject({ phase: 'charts', line: 0, bars: 0 })
    expect(analyzeDemoFrame(5950)).toMatchObject({ phase: 'charts', line: 0.5, bars: 0 })
    expect(analyzeDemoFrame(8300)).toMatchObject({ phase: 'charts', line: 1, bars: 1 })
  })

  it('flashes red, then pops the mistake up with a shake, pinned to the shielded turn', () => {
    expect(analyzeDemoFrame(8500)).toMatchObject({ phase: 'mistake', flash: true, popped: false })
    expect(analyzeDemoFrame(8800)).toMatchObject({ phase: 'mistake', flash: false, popped: true, shake: true })
    expect(analyzeDemoFrame(10000)).toMatchObject({ phase: 'mistake', popped: true, shake: false })
    // the pin sits where the shield went up in the battle (3000 of its 5000 ms)
    expect(MISTAKE_AT).toBeCloseTo(0.6)
  })

  it('counts the score up to the final number, then shows the tier', () => {
    expect(analyzeDemoFrame(11600)).toMatchObject({ phase: 'score', score: 0, tier: false })
    const mid = analyzeDemoFrame(12500)
    expect(mid.phase === 'score' && mid.score > 0 && mid.score < DEMO_SCORE).toBe(true)
    expect(analyzeDemoFrame(14000)).toMatchObject({ phase: 'score', score: DEMO_SCORE, tier: true })
  })

  it('fades out, then loops', () => {
    expect(analyzeDemoFrame(ANALYZE_DEMO_MS - 100)).toMatchObject({ phase: 'fade' })
    expect(analyzeDemoFrame(ANALYZE_DEMO_MS)).toMatchObject({ phase: 'battle', youHp: 1 })
  })
})

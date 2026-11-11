/**
 * The Analyze page's phone demo, one loop: the recorded battle plays back as a mini game, the turns become charts, a
 * red pop-up pins the mistake, and the battle score counts up. Times are milliseconds into the loop.
 */
export const ANALYZE_DEMO_MS = 16200

/** the score the demo lands on */
export const DEMO_SCORE = 78

export type AnalyzeDemoFrame =
  | {
      phase: 'battle'
      phaseMs: number
      /** HP and your energy, 0 to 1 */
      youHp: number
      foeHp: number
      energy: number
      /** your fast move landing on the opponent this instant */
      strike: boolean
      /** the opponent's charged move is on screen */
      foeThrow: boolean
      /** your shield is up against it */
      shielding: boolean
      /** your shields left: two, then one once you've shielded */
      shieldsLeft: number
    }
  | { phase: 'charts'; phaseMs: number; line: number; bars: number }
  | { phase: 'mistake'; phaseMs: number; flash: boolean; popped: boolean; shake: boolean }
  | { phase: 'score'; phaseMs: number; score: number; tier: boolean }
  | { phase: 'fade'; phaseMs: number }

const BATTLE_END = 5000
const CHARTS_END = 8400
const MISTAKE_END = 11600
const SCORE_END = 15600

// the opponent's charged move: thrown at 2400 (GO's shield prompt shows until you tap it), shielded from 3000, gone at 3700
const THROW_START = 2400
const SHIELD_START = 3000
const THROW_END = 3700

/** Where on the battle's timeline (0 to 1) the mistake happened: the shield going up. The charts pin it there. */
export const MISTAKE_AT = SHIELD_START / BATTLE_END

const clamp = (v: number, lo = 0, hi = 1) => Math.min(hi, Math.max(lo, v))
const easeOut = (t: number) => 1 - Math.pow(1 - t, 3)

export function analyzeDemoFrame(ms: number): AnalyzeDemoFrame {
  const t = ((ms % ANALYZE_DEMO_MS) + ANALYZE_DEMO_MS) % ANALYZE_DEMO_MS

  if (t < BATTLE_END) {
    // your fast move lands every 500 ms, theirs every 700 ms
    const yourHits = Math.floor(t / 500)
    const theirHits = Math.floor(t / 700)
    return {
      phase: 'battle',
      phaseMs: t,
      youHp: clamp(1 - theirHits * 0.07),
      foeHp: clamp(1 - yourHits * 0.055),
      energy: clamp(yourHits * 0.11),
      strike: t >= 500 && t % 500 < 120,
      foeThrow: t >= THROW_START && t < THROW_END,
      shielding: t >= SHIELD_START && t < THROW_END,
      shieldsLeft: t >= SHIELD_START ? 1 : 2,
    }
  }
  if (t < CHARTS_END) {
    const p = t - BATTLE_END
    return { phase: 'charts', phaseMs: p, line: clamp((p - 200) / 1500), bars: clamp((p - 1800) / 1200) }
  }
  if (t < MISTAKE_END) {
    const p = t - CHARTS_END
    return { phase: 'mistake', phaseMs: p, flash: p < 250, popped: p >= 250, shake: p >= 250 && p < 750 }
  }
  if (t < SCORE_END) {
    const p = t - MISTAKE_END
    return { phase: 'score', phaseMs: p, score: Math.round(DEMO_SCORE * easeOut(clamp((p - 300) / 1400))), tier: p >= 1800 }
  }
  return { phase: 'fade', phaseMs: t - SCORE_END }
}

/**
 * The home page's phone demo: a Team Builder run played back on a loop. The numbers are a real result, saved from
 * POST /api/team-builder with the eight Popular Pokémon ticked on the checklist (so each battled with good Great
 * League IVs and PvPoke's moves). Rerun it and update this when the meta moves on.
 */
export const DEMO_RUN = {
  league: 'Great League',
  metaDate: '2026-10-04',
  metaSize: 44,
  box: ['medicham', 'azumarill', 'registeel', 'skarmory', 'altaria', 'trevenant', 'lickitung', 'giratina_altered'],
  best: {
    rate: 0.535,
    low: 0.486,
    high: 0.583,
    battles: 400,
    // lead, safe swap, closer
    members: [
      { speciesId: 'giratina_altered', ivs: [3, 7, 6], level: 17, cp: 1495 },
      { speciesId: 'registeel', ivs: [5, 11, 13], level: 23, cp: 1489 },
      { speciesId: 'azumarill', ivs: [1, 7, 12], level: 47, cp: 1494 },
    ],
  },
} as const

export type DemoPhase = 'pick' | 'league' | 'tap' | 'brew' | 'result' | 'fade'

const PICK_EACH_MS = 220
const STEP_MS = 1200
// when each phase starts, in order; the loop ends where 'end' starts
const STARTS: [DemoPhase | 'end', number][] = [
  ['pick', 0],
  ['league', 2400],
  ['tap', 3200],
  ['brew', 4000],
  ['result', 8800],
  ['fade', 15000],
  ['end', 15800],
]
export const DEMO_LOOP_MS = 15800

export interface DemoFrame {
  phase: DemoPhase
  /** how long the current phase has been showing */
  phaseMs: number
  /** Pokémon popped into the box so far */
  picked: number
  /** brewing steps ticked off (0 to 3; the fourth finishes as the result appears) */
  stepsDone: number
  /** how far into the result, for the odds counting up; 0 before it */
  resultMs: number
}

/** Where the demo is, `ms` after it started. */
export function demoFrame(ms: number): DemoFrame {
  const t = ((ms % DEMO_LOOP_MS) + DEMO_LOOP_MS) % DEMO_LOOP_MS
  let index = 0
  while (t >= STARTS[index + 1][1]) index++
  const [phase, start] = STARTS[index] as [DemoPhase, number]
  const brewStart = STARTS[3][1]
  const resultStart = STARTS[4][1]
  return {
    phase,
    phaseMs: t - start,
    picked: Math.min(DEMO_RUN.box.length, Math.floor(t / PICK_EACH_MS) + 1),
    stepsDone: t < brewStart ? 0 : Math.min(3, Math.floor((t - brewStart) / STEP_MS)),
    resultMs: Math.max(0, t - resultStart),
  }
}

const TYPE_MS_PER_CHAR = 25

/** The part of a caption typed out after `ms`. */
export function typed(text: string, ms: number): string {
  return text.slice(0, Math.floor(ms / TYPE_MS_PER_CHAR))
}

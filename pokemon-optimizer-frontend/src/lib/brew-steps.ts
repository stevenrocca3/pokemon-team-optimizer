/**
 * Timelines for the home page's "Four Steps" carousel: each slide's little pixel screen loops one of these.
 * Pure functions of milliseconds played, so the animations are testable and freeze cleanly with reduced motion.
 */

import { DEMO_RUN } from './demo-run'

const clamp01 = (x: number) => Math.min(1, Math.max(0, x))
const within = (t: number, loopMs: number) => ((t % loopMs) + loopMs) % loopMs

/** A carousel index stepped past either end, wrapped around. */
export function wrapIndex(i: number, n: number): number {
  return ((i % n) + n) % n
}

// 1. Grind: one 1v1 after another

export const GRIND_ROUND_MS = 4000
const YOU_FAST_AT = [300, 700, 1100, 1500]
const FOE_FAST_AT = [500, 1300]
const LUNGE_MS = 150
const CHARGE_AT = 1900
const KO_AT = 2300
const LEAVE_AT = 3400

export type GrindAttack = 'fast' | 'charged' | 'foe-fast' | 'foe-charged' | null

export interface GrindFrame {
  /** which 1v1 this is, counting from 0 */
  round: number
  phaseMs: number
  youHp: number
  foeHp: number
  /** your charged-move meter, 0 to 100 */
  energy: number
  attack: GrindAttack
  /** null until someone faints */
  won: boolean | null
  /** fading out before the next 1v1 */
  leaving: boolean
}

/** Every third 1v1 goes the foe's way: the screen shows wins and losses being recorded, not a sweep. */
export function grindFrame(ms: number): GrindFrame {
  const round = Math.floor(Math.max(0, ms) / GRIND_ROUND_MS)
  const t = within(ms, GRIND_ROUND_MS)
  const youWin = round % 3 !== 2
  const hits = YOU_FAST_AT.filter((at) => t >= at).length
  const foeHits = FOE_FAST_AT.filter((at) => t >= at).length
  const ko = t >= KO_AT

  let attack: GrindAttack = null
  if (YOU_FAST_AT.some((at) => t >= at && t < at + LUNGE_MS)) attack = 'fast'
  else if (FOE_FAST_AT.some((at) => t >= at && t < at + LUNGE_MS)) attack = 'foe-fast'
  else if (t >= CHARGE_AT && t < KO_AT) attack = youWin ? 'charged' : 'foe-charged'

  return {
    round,
    phaseMs: t,
    youHp: !youWin && ko ? 0 : 100 - 14 * foeHits,
    foeHp: youWin && ko ? 0 : 100 - 12 * hits,
    energy: youWin && t >= CHARGE_AT ? 0 : 25 * hits,
    attack,
    won: ko ? youWin : null,
    leaving: t >= LEAVE_AT,
  }
}

// 2. Dose: Pokémon stack into team folders, the teams into the ranked root

export const DOSE_LOOP_MS = 5000

export interface DoseFrame {
  /** how far the Pokémon have travelled up into their team folders, 0 to 1 */
  leafProgress: number
  /** the same for each team folder travelling into the root */
  teamProgress: [number, number]
  /** team folders that have landed in the root */
  stacked: number
  glow: boolean
  fading: boolean
}

export function doseFrame(ms: number): DoseFrame {
  const t = within(ms, DOSE_LOOP_MS)
  const teamProgress: [number, number] = [clamp01((t - 1600) / 900), clamp01((t - 1900) / 900)]
  return {
    leafProgress: clamp01((t - 200) / 1200),
    teamProgress,
    stacked: teamProgress.filter((p) => p === 1).length,
    glow: t >= 3000 && t < 4600,
    fading: t >= 4600,
  }
}

// 3. Pull: full 3v3s, every lineup

export const LINEUP_MS = 2000

/** Every order of three, the given one first. */
export function lineups<T>(ids: readonly T[]): T[][] {
  const [a, b, c] = ids
  return [
    [a, b, c],
    [a, c, b],
    [b, a, c],
    [b, c, a],
    [c, a, b],
    [c, b, a],
  ]
}

export interface PullFrame {
  /** which of the six lineups is playing */
  lineup: number
  phaseMs: number
  /** 0 lead, 1 safe swap, 2 closer: who is on the field */
  active: 0 | 1 | 2
  shieldsLeft: number
  /** which of the 100 meta teams this lineup is on */
  metaTeam: number
  /** the slots sliding into the new order */
  shuffling: boolean
  /** a shield popping right now */
  shielding: boolean
  hitting: boolean
}

export function pullFrame(ms: number): PullFrame {
  const t = within(ms, LINEUP_MS * 6)
  const p = t % LINEUP_MS
  return {
    lineup: Math.floor(t / LINEUP_MS),
    phaseMs: p,
    active: p < 900 ? 0 : p < 1300 ? 1 : 2,
    shieldsLeft: p < 650 ? 2 : 1,
    metaTeam: Math.min(100, Math.floor(p / 19) + 1),
    shuffling: p < 250,
    shielding: p >= 650 && p < 850,
    hitting: [350, 1000, 1450, 1650].some((at) => p >= at && p < at + 140),
  }
}

// 4. Taste: the 400 held-out battles

export const TASTE_LOOP_MS = 7200
const BATTLE_MS = 7

/** `total` battles, exactly `wins` of them won, scattered in a fixed order (the same every visit). */
export function battleGrid(total: number, wins: number): boolean[] {
  const order = Array.from({ length: total }, (_, i) => i)
  let seed = 20261004
  for (let i = total - 1; i > 0; i--) {
    seed = (seed * 1664525 + 1013904223) >>> 0
    const j = seed % (i + 1)
    ;[order[i], order[j]] = [order[j], order[i]]
  }
  const grid = new Array<boolean>(total).fill(false)
  for (let k = 0; k < wins; k++) grid[order[k]] = true
  return grid
}

export const TASTE_BATTLES = DEMO_RUN.best.battles
export const TASTE_WINS = Math.round(DEMO_RUN.best.rate * TASTE_BATTLES) // the saved run: 214 of 400
export const TASTE_GRID = battleGrid(TASTE_BATTLES, TASTE_WINS)
const WINS_BEFORE = TASTE_GRID.reduce<number[]>((acc, won, i) => {
  acc.push((acc[i - 1] ?? 0) + (won ? 1 : 0))
  return acc
}, [])

export interface TasteFrame {
  revealed: number
  wins: number
  showRange: boolean
  fading: boolean
}

export function tasteFrame(ms: number): TasteFrame {
  const t = within(ms, TASTE_LOOP_MS)
  const revealed = Math.min(TASTE_BATTLES, Math.floor(t / BATTLE_MS))
  return {
    revealed,
    wins: revealed === 0 ? 0 : WINS_BEFORE[revealed - 1],
    showRange: t >= 3000,
    fading: t >= 6600,
  }
}

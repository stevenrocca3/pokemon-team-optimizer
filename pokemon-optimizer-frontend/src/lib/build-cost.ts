import type { TeamMember } from '../types'

/** What you have: Candy and Candy XL for the Pokémon's family, and Stardust. Null until you type it in. */
export interface Inventory {
  candy: number | null
  xlCandy: number | null
  stardust: number | null
}

/** Everything to make an evolved Team member battle-ready (#139): evolve, power up, unlock its second move. */
export interface BuildTotals {
  candy: number
  xlCandy: number
  stardust: number
  /** the power-up part is known: false when its current level isn't */
  powerUpKnown: boolean
  items: { name: string; count: number }[]
  lures: string[]
}

export function buildTotals(member: TeamMember): BuildTotals {
  const evolution = member.evolution?.cost
  const powerUp = member.cost
  const second = evolution?.secondMove
  const steps = evolution?.steps ?? []
  return {
    candy: (evolution?.evolveCandy ?? 0) + (powerUp?.candy ?? 0) + (second?.candy ?? 0),
    xlCandy: powerUp?.xlCandy ?? 0,
    stardust: (powerUp?.stardust ?? 0) + (second?.stardust ?? 0),
    powerUpKnown: powerUp !== null,
    items: steps.flatMap((s) => (s.item ? [{ name: s.item, count: s.itemCount }] : [])),
    lures: steps.flatMap((s) => (s.lure ? [s.lure] : [])),
  }
}

/** One line per thing you're short of, like "Insufficient Candy XL: short 45". Empty when you have enough. */
export function shortfalls(totals: BuildTotals, inventory: Inventory): string[] {
  const needs: [string, number, number | null][] = [
    ['Candy', totals.candy, inventory.candy],
    ['Candy XL', totals.xlCandy, inventory.xlCandy],
    ['Stardust', totals.stardust, inventory.stardust],
  ]
  return needs
    .filter(([, need, have]) => have !== null && have < need)
    .map(([name, need, have]) => `Insufficient ${name}: short ${(need - (have ?? 0)).toLocaleString('en-US')}`)
}

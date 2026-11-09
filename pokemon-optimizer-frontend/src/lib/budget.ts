import type { TeamMember, TeamResult } from '../types'

export interface TeamCost {
  stardust: number
  candy: number
  xlCandy: number
  /** members whose cost isn't known: checklist ticks and ones added without a CP */
  unknown: number
}

export function teamCost(members: TeamMember[]): TeamCost {
  const total: TeamCost = { stardust: 0, candy: 0, xlCandy: 0, unknown: 0 }
  for (const m of members) {
    if (m.cost === null) {
      total.unknown++
    } else {
      total.stardust += m.cost.stardust
      total.candy += m.cost.candy
      total.xlCandy += m.cost.xlCandy
    }
  }
  return total
}

/** The best-ranked team whose known Stardust fits the budget, or null. Teams arrive best first. */
export function bestAffordable(teams: TeamResult[], stardust: number): number | null {
  const i = teams.findIndex((t) => teamCost(t.members).stardust <= stardust)
  return i === -1 ? null : i
}

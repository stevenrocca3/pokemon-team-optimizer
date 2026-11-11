import { describe, expect, it } from 'vitest'
import type { TeamMember, TeamResult } from '../types'
import { bestAffordable, teamCost } from './budget'

const member = (cost: TeamMember['cost']): TeamMember => ({
  index: 0, speciesId: 'medicham', shadow: false, level: 50, cp: 1439, atkIv: 0, defIv: 15, staIv: 15,
  fastMove: 'COUNTER', chargedMoves: ['ICE_PUNCH'], metaRating: 500, assumed: cost === null, powerUp: null, cost, evolution: null,
})
const team = (...costs: TeamMember['cost'][]): TeamResult => ({
  members: costs.map(member), winRate: { rate: 0.5, low: 0.45, high: 0.55, battles: 400 }, toughest: [], suggestions: [], coverage: { rating: 500, answered: 0, metaSize: 44 }, threats: [],
})

describe('teamCost', () => {
  it('adds up the members it knows and counts the ones it does not', () => {
    expect(teamCost(team({ stardust: 20_500, candy: 19, xlCandy: 0 }, { stardust: 250_000, candy: 0, xlCandy: 296 }, null).members))
      .toEqual({ stardust: 270_500, candy: 19, xlCandy: 296, unknown: 1 })
  })
})

describe('bestAffordable', () => {
  const teams = [
    team({ stardust: 300_000, candy: 0, xlCandy: 0 }),
    team({ stardust: 90_000, candy: 0, xlCandy: 0 }),
    team({ stardust: 10_000, candy: 0, xlCandy: 0 }),
  ]

  it('picks the best-ranked team that fits', () => {
    expect(bestAffordable(teams, 100_000)).toBe(1)
    expect(bestAffordable(teams, 300_000)).toBe(0)
  })

  it('is null when none fits', () => {
    expect(bestAffordable(teams, 5_000)).toBeNull()
  })
})

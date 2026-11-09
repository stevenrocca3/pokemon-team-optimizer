import { describe, expect, it } from 'vitest'
import { niceFloor, proofStats } from './site-stats'
import type { SiteStats } from '../types'

const stats = (over: Partial<SiteStats> = {}): SiteStats => ({
  teamsBuilt: 0,
  pokemonSaved: 0,
  speciesTicked: 0,
  trainers: 0,
  ratings: { total: 0, average: 0, percentByStars: {} },
  ...over,
})

describe('niceFloor', () => {
  it('rounds down to two significant figures, never up', () => {
    expect(niceFloor(12_345)).toBe(12_000)
    expect(niceFloor(1_299)).toBe(1_200)
    expect(niceFloor(999)).toBe(990)
  })
})

describe('proofStats', () => {
  it('shows nothing while the numbers are small', () => {
    expect(proofStats(stats({ teamsBuilt: 40, pokemonSaved: 300, trainers: 12 }))).toEqual([])
  })

  it('shows teams built once there are a thousand, rounded down with a plus', () => {
    expect(proofStats(stats({ teamsBuilt: 1_234 }))).toEqual([{ value: 1_200, suffix: '+', label: 'teams built' }])
  })

  it('counts checklist ticks and exact Pokémon together as Pokémon saved', () => {
    expect(proofStats(stats({ pokemonSaved: 9_000, speciesTicked: 3_500 }))).toEqual([
      { value: 12_000, suffix: '+', label: 'Pokémon saved' },
    ])
  })

  it('shows signed-in players from 500', () => {
    expect(proofStats(stats({ trainers: 499 }))).toEqual([])
    expect(proofStats(stats({ trainers: 640 }))).toEqual([{ value: 640, suffix: '+', label: 'players signed in' }])
  })

  it('shows the rating only with enough ratings and a good average', () => {
    const rated = (total: number, average: number) => stats({ ratings: { total, average, percentByStars: {} } })
    expect(proofStats(rated(24, 4.8))).toEqual([])
    expect(proofStats(rated(80, 3.6))).toEqual([])
    expect(proofStats(rated(80, 4.64))).toEqual([{ value: 4.6, suffix: '/5', label: 'from 80 ratings', decimals: 1 }])
  })
})

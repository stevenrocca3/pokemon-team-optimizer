import { describe, expect, it } from 'vitest'
import type { CaughtPokemon } from '../types'
import { buildCandidates, copyLabel, pickCopies, shieldsLabel, winnersLabel, winRateEdge } from './compare'

const medicham = (id: number, atkIv: number, fastMove = 'COUNTER'): CaughtPokemon => ({
  id, speciesId: 'medicham', atkIv, defIv: 15, staIv: 15, level: 20, shadow: false, fastMove, chargedMoves: ['ICE_PUNCH'],
})

describe('pickCopies', () => {
  it('keeps the best six by stat product rank and drops ones without moves', () => {
    const copies = [medicham(1, 15), medicham(2, 0), medicham(3, 1, ''), ...[4, 5, 6, 7, 8].map((id) => medicham(id, id))]
    // a lower attack IV ranks better for Medicham in Great League; rank = atkIv + 1 here
    const picked = pickCopies(copies, (p) => p.atkIv + 1)
    expect(picked.map((p) => p.id)).toEqual([2, 4, 5, 6, 7, 8])
  })
})

describe('labels', () => {
  it('names a copy by its IVs', () => {
    expect(copyLabel({ atkIv: 0, defIv: 15, staIv: 15, shadow: false })).toBe('0/15/15')
    expect(copyLabel({ atkIv: 4, defIv: 14, staIv: 15, shadow: true })).toBe('Shadow 4/14/15')
  })

  it('says how many shields each side had', () => {
    expect([0, 1, 2].map(shieldsLabel)).toEqual(['no shields', '1 shield each', '2 shields each'])
  })

  it('reads naturally for one winner or several', () => {
    expect(winnersLabel(['0/15/15'])).toBe('only 0/15/15 wins')
    expect(winnersLabel(['0/15/15', '1/14/15'])).toBe('0/15/15 and 1/14/15 win')
    expect(winnersLabel(['0/15/15', '1/14/15', '2/15/14'])).toBe('0/15/15, 1/14/15 and 2/15/14 win')
  })
})

describe('Which One to Build (#118)', () => {
  // Species with two or more battle-ready copies are the ones worth comparing, most copies first
  it('finds the species with 2+ copies that have moves', () => {
    const azumarill = (id: number): CaughtPokemon => ({ ...medicham(id, 0), speciesId: 'azumarill' })
    const list = [medicham(1, 0), azumarill(2), medicham(3, 4), azumarill(4), medicham(5, 8), { ...azumarill(6), fastMove: '' }]
    expect(buildCandidates(list)).toEqual([
      { speciesId: 'medicham', copies: 3 },
      { speciesId: 'azumarill', copies: 2 },
    ])
    expect(buildCandidates([medicham(1, 0)])).toEqual([])
  })

  // The badge's number: the gap in meta win rate, in percentage points to one decimal
  it('measures the win-rate edge in percentage points', () => {
    expect(winRateEdge({ wins: 60, battles: 90 }, { wins: 49, battles: 90 })).toBe(12.2)
    expect(winRateEdge({ wins: 45, battles: 90 }, { wins: 45, battles: 90 })).toBe(0)
  })
})

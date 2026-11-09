import { describe, expect, it } from 'vitest'
import type { CaughtPokemon, CaughtPokemonRequest } from '../types'
import { MAX_COLLECTION, mergeInto } from './merge'

const medicham = (atkIv: number, level = 40): CaughtPokemonRequest => ({
  speciesId: 'medicham', atkIv, defIv: 15, staIv: 15, level, shadow: false, fastMove: 'COUNTER', chargedMoves: ['ICE_PUNCH'],
})

function collectionOf(size: number): CaughtPokemon[] {
  // distinct Pokémon: vary the IVs past what a real one can have, only identity matters here
  return Array.from({ length: size }, (_, i) => ({ ...medicham(16 + i), id: i + 1 }))
}

describe('mergeInto', () => {
  it('matches the server: a Collection holds at most 10,000 Pokémon', () => {
    expect(MAX_COLLECTION).toBe(10_000)
  })

  it('refuses a batch that would push the Collection past the cap, and changes nothing', () => {
    let id = 100_000
    expect(() => mergeInto(collectionOf(9_999), [medicham(1), medicham(2)], () => ++id)).toThrow(
      'A Collection holds up to 10,000 Pokémon, and yours has room for 1 more.',
    )
  })

  it('still accepts a batch that exactly fills it, and duplicates never count against the cap', () => {
    let id = 100_000
    const full = collectionOf(9_999)
    const again = { ...full[0] } // already there, so it doesn't need room
    const result = mergeInto(full, [medicham(1), again], () => ++id)
    expect(result.list).toHaveLength(10_000)
    expect(result.added).toBe(1)
    expect(result.skipped).toBe(1)
  })

  it('tells a full Collection to make room', () => {
    let id = 100_000
    expect(() => mergeInto(collectionOf(10_000), [medicham(1)], () => ++id)).toThrow(
      'Your Collection is full (10,000 Pokémon). Remove some to add more.',
    )
  })
})

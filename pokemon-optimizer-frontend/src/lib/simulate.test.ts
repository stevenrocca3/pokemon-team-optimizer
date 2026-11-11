import { describe, expect, it } from 'vitest'
import type { CaughtPokemonRequest } from '../types'
import { withSimulation, type Simulation } from './simulate'

const mudkip: CaughtPokemonRequest = { speciesId: 'mudkip', atkIv: 0, defIv: 15, staIv: 15, level: 15, fastMove: 'TACKLE', chargedMoves: ['DIG'], shadow: false }
const swampert: CaughtPokemonRequest = { ...mudkip, speciesId: 'swampert', fastMove: 'MUD_SHOT', chargedMoves: ['HYDRO_CANNON', 'EARTHQUAKE'] }
const medicham: CaughtPokemonRequest = { speciesId: 'medicham', atkIv: 0, defIv: 15, staIv: 15, level: 20, fastMove: 'COUNTER', chargedMoves: ['ICE_PUNCH'], shadow: false }
const simulation: Simulation = { league: 'ULTRA', from: mudkip, as: swampert, fromName: 'Mudkip', asName: 'Swampert' }

describe('withSimulation', () => {
  it('swaps your Pokémon for its evolved form, in place', () => {
    expect(withSimulation([medicham, mudkip], simulation)).toEqual([medicham, swampert])
  })

  // the same Mudkip with different moves is still that Mudkip: moves don't survive evolving anyway
  it('matches on species, IVs and Shadow, not moves', () => {
    expect(withSimulation([{ ...mudkip, chargedMoves: ['MUD_BOMB'] }], simulation)).toEqual([swampert])
  })

  it('swaps only one when you have two the same', () => {
    expect(withSimulation([mudkip, mudkip], simulation)).toEqual([swampert, mudkip])
  })

  // a Mudkip with no moves isn't sent to the Team Builder, so the evolved one is added instead
  it('adds the evolved form when the original isn’t there', () => {
    expect(withSimulation([medicham], simulation)).toEqual([medicham, swampert])
  })
})

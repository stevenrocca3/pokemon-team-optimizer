import type { CaughtPokemonRequest, TeamBuilderLeague } from '../types'
import { sameness } from '../collection/merge'

/**
 * "Simulate in Team Builder" from the Collection (#140): one of your Pokémon, swapped for a form it can evolve into,
 * in the League you picked. Sent as router state to /team-builder.
 */
export interface Simulation {
  league: TeamBuilderLeague
  from: CaughtPokemonRequest
  as: CaughtPokemonRequest
  fromName: string
  asName: string
}

/** Your Pokémon with the simulated one evolved: the matching one is swapped in place, or the evolved one added. */
export function withSimulation(pokemon: CaughtPokemonRequest[], simulation: Simulation): CaughtPokemonRequest[] {
  // moves aren't part of it: evolving rerolls them
  const at = pokemon.findIndex((p) => sameness(p) === sameness(simulation.from))
  if (at === -1) return [...pokemon, simulation.as]
  return pokemon.map((p, i) => (i === at ? simulation.as : p))
}

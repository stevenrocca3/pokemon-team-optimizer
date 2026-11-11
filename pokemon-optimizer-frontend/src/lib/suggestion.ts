import type { CaughtPokemon, CaughtPokemonRequest, Suggestion } from '../types'

/** A suggestion as a Pokémon to battle with: the meta's IVs, level and moves (what Add to Box saves). */
export function suggestionBuild(s: Suggestion): CaughtPokemonRequest {
  const { speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow } = s
  return { speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow }
}

/** A saved Pokémon as the Team Builder takes it. */
export function asRequest(p: CaughtPokemon): CaughtPokemonRequest {
  const { speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow } = p
  return { speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow }
}

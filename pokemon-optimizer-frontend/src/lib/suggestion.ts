import type { CaughtPokemonRequest, Suggestion } from '../types'

/** A suggestion as a Pokémon to battle with: the meta's IVs, level and moves (what Add to Box saves). */
export function suggestionBuild(s: Suggestion): CaughtPokemonRequest {
  const { speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow } = s
  return { speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow }
}

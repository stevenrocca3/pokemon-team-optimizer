import type { ImportedPokemon, SuggestedMoves } from '../types'

export interface MoveChoice {
  fast: string
  charged1: string
  /** '' = only one charged move */
  charged2: string
}

/**
 * The moves an imported Pokémon starts with: what the export recorded, with any gap filled from PvPoke's
 * suggested moveset. A fast or charged move left as '' still has to be picked by hand.
 */
export function startingMoves(pokemon: ImportedPokemon, suggested: SuggestedMoves | undefined): MoveChoice {
  if (!pokemon.needsMoves || !suggested) {
    return { fast: pokemon.fastMove ?? '', charged1: pokemon.chargedMoves[0] ?? '', charged2: pokemon.chargedMoves[1] ?? '' }
  }
  const charged = pokemon.chargedMoves.length > 0 ? pokemon.chargedMoves : suggested.chargedMoves
  return { fast: pokemon.fastMove ?? suggested.fastMove, charged1: charged[0] ?? '', charged2: charged[1] ?? '' }
}

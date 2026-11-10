import type { Ivs } from '../cp'
import type { ImportPreview, Learnset, SpeciesDetail, SuggestedMoves } from '../../types'
import { solve, type Candidate } from './solve'

export type Resolution = { kind: 'ready'; level: number } | { kind: 'incomplete' } | { kind: 'mismatch' }

/** Whether the species and CP the player confirmed fit the bars (and the HP, when it was read). */
export function resolve(ivs: Ivs, hp: number | null, candidate: Candidate | undefined, cp: number | null): Resolution {
  if (!candidate || cp === null) return { kind: 'incomplete' }
  const matches = solve([candidate], [cp], hp, ivs)
  return matches.length > 0 ? { kind: 'ready', level: matches[0].level } : { kind: 'mismatch' }
}

export interface ReadyPokemon {
  speciesId: string
  ivs: Ivs
  level: number
  shadow: boolean
}

/** The same preview a CSV import gets, so screenshots go through the same move picking and saving. */
export function toImportPreview(pokemon: ReadyPokemon[], details: Map<string, SpeciesDetail>): ImportPreview {
  const learnsets: Record<string, Learnset> = {}
  const suggestedMoves: Record<string, SuggestedMoves> = {}
  for (const { speciesId } of pokemon) {
    const detail = details.get(speciesId)
    if (!detail) continue
    learnsets[speciesId] = detail.learnset
    if (detail.suggestedMoves) suggestedMoves[speciesId] = detail.suggestedMoves
  }
  return {
    pokemon: pokemon.map((p, i) => ({
      index: i + 1,
      speciesId: p.speciesId,
      atkIv: p.ivs.atk,
      defIv: p.ivs.def,
      staIv: p.ivs.sta,
      level: p.level,
      shadow: p.shadow,
      fastMove: null,
      chargedMoves: [],
      needsMoves: true,
    })),
    rejected: [],
    learnsets,
    suggestedMoves,
  }
}

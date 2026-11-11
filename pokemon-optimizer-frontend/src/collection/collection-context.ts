import { createContext, useContext } from 'react'
import type { CaughtPokemon, CaughtPokemonRequest, OwnedSpecies, Suggestion, TeamBuilderLeague } from '../types'

/**
 * Where a player's Pokémon live: their account when logged in, this browser when not.
 * Pages use this and never need to know which.
 */
export type Storage = 'account' | 'browser'

export interface AddAllResult {
  added: number
  raised: number
  skipped: number
}

export interface ChecklistSave {
  saving: boolean
  error: string | null
}

export type CollectionState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | {
      status: 'ready'
      storage: Storage
      /** newest first */
      list: CaughtPokemon[]
      /** resolves to false when it was already in the Collection */
      add: (pokemon: CaughtPokemonRequest) => Promise<boolean>
      addAll: (pokemon: CaughtPokemonRequest[]) => Promise<AddAllResult>
      /** a Team Builder suggestion into the box with its meta build; added is false when it was already there */
      addSuggestion: (
        suggestion: Suggestion,
        league: TeamBuilderLeague,
        cup: string | null,
      ) => Promise<{ pokemon: CaughtPokemon; added: boolean }>
      remove: (id: number) => Promise<void>
      /** species ticked as owned without exact IVs; the Team Builder assumes good IVs for them */
      checklist: OwnedSpecies[]
      /** updates at once; saving to the account happens behind it (see checklistSave) */
      setChecklist: (next: OwnedSpecies[]) => void
      checklistSave: ChecklistSave
    }

export const CollectionContext = createContext<CollectionState>({ status: 'loading' })

export function useCollection(): CollectionState {
  return useContext(CollectionContext)
}

import { createContext, useContext } from 'react'
import type { CaughtPokemon, CaughtPokemonRequest } from '../types'

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
      remove: (id: number) => Promise<void>
    }

export const CollectionContext = createContext<CollectionState>({ status: 'loading' })

export function useCollection(): CollectionState {
  return useContext(CollectionContext)
}

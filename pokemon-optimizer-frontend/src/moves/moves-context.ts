import { createContext, useContext } from 'react'
import type { Move } from '../types'

export type MovesState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; byId: Map<string, Move> }

export const MovesContext = createContext<MovesState>({ status: 'loading' })

/** Every move by ID, loaded once for the whole app by MovesProvider. */
export function useMoves(): MovesState {
  return useContext(MovesContext)
}

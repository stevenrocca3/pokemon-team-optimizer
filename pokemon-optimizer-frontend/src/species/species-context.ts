import { createContext, useContext } from 'react'
import type { SpeciesOption } from '../types'

export type SpeciesState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; list: SpeciesOption[]; byId: Map<string, SpeciesOption> }

export const SpeciesContext = createContext<SpeciesState>({ status: 'loading' })

/** The full species list, loaded once for the whole app by SpeciesProvider. */
export function useSpecies(): SpeciesState {
  return useContext(SpeciesContext)
}

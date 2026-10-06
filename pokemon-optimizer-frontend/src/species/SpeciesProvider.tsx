import { useEffect, useState, type ReactNode } from 'react'
import { getSpecies } from '../api'
import { sortByDex } from '../lib/species'
import { SpeciesContext, type SpeciesState } from './species-context'

export function SpeciesProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SpeciesState>({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    getSpecies()
      .then((list) => {
        if (cancelled) return
        const sorted = sortByDex(list)
        setState({ status: 'ready', list: sorted, byId: new Map(sorted.map((s) => [s.id, s])) })
      })
      .catch((error: unknown) => {
        if (cancelled) return
        setState({ status: 'error', message: error instanceof Error ? error.message : String(error) })
      })
    return () => {
      cancelled = true
    }
  }, [])

  return <SpeciesContext.Provider value={state}>{children}</SpeciesContext.Provider>
}

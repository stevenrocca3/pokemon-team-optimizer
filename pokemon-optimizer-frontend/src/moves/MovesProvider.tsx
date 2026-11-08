import { useEffect, useState, type ReactNode } from 'react'
import { getMoves } from '../api'
import type { Move } from '../types'
import { MovesContext, type MovesState } from './moves-context'

// Fetched once per page load, the first time a provider mounts. Only pages that show moves mount one,
// so other pages don't spend bandwidth on them.
let movesRequest: Promise<Move[]> | null = null

export function MovesProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<MovesState>({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    movesRequest ??= getMoves()
    movesRequest
      .then((list) => {
        if (cancelled) return
        setState({ status: 'ready', byId: new Map(list.map((m) => [m.moveId, m])) })
      })
      .catch((error: unknown) => {
        movesRequest = null // let the next visit retry
        if (cancelled) return
        setState({ status: 'error', message: error instanceof Error ? error.message : String(error) })
      })
    return () => {
      cancelled = true
    }
  }, [])

  return <MovesContext.Provider value={state}>{children}</MovesContext.Provider>
}

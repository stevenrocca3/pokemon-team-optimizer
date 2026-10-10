import { useEffect, useState, type ReactNode } from 'react'
import { getMoves } from '../api'
import { MovesContext, type MovesState } from './moves-context'

export function MovesProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<MovesState>({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    getMoves()
      .then((list) => {
        if (cancelled) return
        setState({ status: 'ready', byId: new Map(list.map((m) => [m.moveId, m])) })
      })
      .catch((error: unknown) => {
        if (cancelled) return
        setState({ status: 'error', message: error instanceof Error ? error.message : String(error) })
      })
    return () => {
      cancelled = true
    }
  }, [])

  return <MovesContext.Provider value={state}>{children}</MovesContext.Provider>
}

import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { addAllToCollection, addToCollection, getCollection, NotLoggedInError, removeFromCollection } from '../api'
import type { CaughtPokemon, CaughtPokemonRequest } from '../types'
import { CollectionContext, type AddAllResult, type CollectionState, type Storage } from './collection-context'
import { loadGuestCollection, saveGuestCollection } from './guest-storage'
import { mergeInto, sameness } from './merge'

const newestFirst = (list: CaughtPokemon[]) => [...list].sort((a, b) => b.id - a.id)

type Loaded = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; storage: Storage; list: CaughtPokemon[] }

/** Loads the Collection once for the whole app: from the account, or from this browser for a guest. */
export function CollectionProvider({ children }: { children: ReactNode }) {
  const [loaded, setLoaded] = useState<Loaded>({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    getCollection()
      .then((list) => {
        if (!cancelled) setLoaded({ status: 'ready', storage: 'account', list: newestFirst(list) })
      })
      .catch((error: unknown) => {
        if (cancelled) return
        if (error instanceof NotLoggedInError) setLoaded({ status: 'ready', storage: 'browser', list: newestFirst(loadGuestCollection()) })
        else setLoaded({ status: 'error', message: error instanceof Error ? error.message : String(error) })
      })
    return () => {
      cancelled = true
    }
  }, [])

  const storage = loaded.status === 'ready' ? loaded.storage : null
  const list = loaded.status === 'ready' ? loaded.list : null

  const setList = useCallback((next: CaughtPokemon[]) => {
    setLoaded((current) => (current.status === 'ready' ? { ...current, list: newestFirst(next) } : current))
  }, [])

  const addAll = useCallback(
    async (incoming: CaughtPokemonRequest[]): Promise<AddAllResult> => {
      if (!list) throw new Error('Collection is still loading')
      if (storage === 'browser') {
        let nextId = list.reduce((max, p) => Math.max(max, p.id), 0)
        const merged = mergeInto(list, incoming, () => ++nextId)
        if (!saveGuestCollection(merged.list)) throw new Error("This browser won't store data (private window or storage blocked)")
        setList(merged.list)
        return { added: merged.added, raised: merged.raised, skipped: merged.skipped }
      }
      // the server skips duplicates the same way; reload to show exactly what it kept
      const saved = await addAllToCollection(incoming)
      const fresh = await getCollection()
      setList(fresh)
      const added = fresh.length - list.length
      return { added, raised: saved.length - added, skipped: incoming.length - saved.length }
    },
    [list, storage, setList],
  )

  const add = useCallback(
    async (pokemon: CaughtPokemonRequest): Promise<boolean> => {
      if (!list) throw new Error('Collection is still loading')
      if (storage === 'browser') return (await addAll([pokemon])).added === 1
      if (list.some((p) => sameness(p) === sameness(pokemon))) return false
      const saved = await addToCollection(pokemon)
      setList([saved, ...list])
      return true
    },
    [list, storage, addAll, setList],
  )

  const remove = useCallback(
    async (id: number) => {
      if (!list) return
      const next = list.filter((p) => p.id !== id)
      if (storage === 'browser') {
        saveGuestCollection(next)
      } else {
        await removeFromCollection(id)
      }
      setList(next)
    },
    [list, storage, setList],
  )

  const value = useMemo<CollectionState>(
    () => (loaded.status === 'ready' ? { ...loaded, add, addAll, remove } : loaded),
    [loaded, add, addAll, remove],
  )

  return <CollectionContext.Provider value={value}>{children}</CollectionContext.Provider>
}

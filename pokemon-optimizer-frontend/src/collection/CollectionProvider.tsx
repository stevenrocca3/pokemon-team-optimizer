import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { addAllToCollection, addToCollection, getChecklist, getCollection, NotLoggedInError, removeFromCollection, saveChecklist } from '../api'
import type { CaughtPokemon, CaughtPokemonRequest, OwnedSpecies } from '../types'
import { CollectionContext, type AddAllResult, type ChecklistSave, type CollectionState, type Storage } from './collection-context'
import { loadGuestChecklist, loadGuestCollection, saveGuestChecklist, saveGuestCollection } from './guest-storage'
import { latestSaver, mergeOwned } from './checklist'
import { mergeInto, sameness } from './merge'

const newestFirst = (list: CaughtPokemon[]) => [...list].sort((a, b) => b.id - a.id)

type Loaded =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; storage: Storage; list: CaughtPokemon[]; checklist: OwnedSpecies[] }

/** Loads the Collection once for the whole app: from the account, or from this browser for a guest. */
export function CollectionProvider({ children }: { children: ReactNode }) {
  const [loaded, setLoaded] = useState<Loaded>({ status: 'loading' })
  const [checklistSave, setChecklistSave] = useState<ChecklistSave>({ saving: false, error: null })
  // one saver for the app's lifetime, so overlapping ticks queue behind each other (see latestSaver)
  const [saveLatest] = useState(() => latestSaver(saveChecklist, setChecklistSave))

  useEffect(() => {
    let cancelled = false
    Promise.all([getCollection(), getChecklist()])
      .then(([list, checklist]) => {
        if (cancelled) return
        // ticks made before logging in join the account's; unlike Pokémon they can't clash, so no need to ask
        const guestTicks = loadGuestChecklist()
        const merged = mergeOwned(checklist, guestTicks)
        setLoaded({ status: 'ready', storage: 'account', list: newestFirst(list), checklist: merged })
        if (guestTicks.length > 0) {
          saveChecklist(merged)
            .then(() => saveGuestChecklist([]))
            .catch((error: unknown) => setChecklistSave({ saving: false, error: error instanceof Error ? error.message : String(error) }))
        }
      })
      .catch((error: unknown) => {
        if (cancelled) return
        if (error instanceof NotLoggedInError) {
          setLoaded({ status: 'ready', storage: 'browser', list: newestFirst(loadGuestCollection()), checklist: loadGuestChecklist() })
        } else {
          setLoaded({ status: 'error', message: error instanceof Error ? error.message : String(error) })
        }
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

  const setChecklist = useCallback(
    (next: OwnedSpecies[]) => {
      setLoaded((current) => (current.status === 'ready' ? { ...current, checklist: next } : current))
      if (storage === 'browser') {
        setChecklistSave({
          saving: false,
          error: saveGuestChecklist(next) ? null : "This browser won't store data (private window or storage blocked)",
        })
      } else {
        saveLatest(next)
      }
    },
    [storage, saveLatest],
  )

  const value = useMemo<CollectionState>(
    () => (loaded.status === 'ready' ? { ...loaded, add, addAll, remove, setChecklist, checklistSave } : loaded),
    [loaded, add, addAll, remove, setChecklist, checklistSave],
  )

  return <CollectionContext.Provider value={value}>{children}</CollectionContext.Provider>
}

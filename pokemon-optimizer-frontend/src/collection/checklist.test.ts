import { describe, expect, it } from 'vitest'
import type { OwnedSpecies } from '../types'
import { latestSaver, mergeOwned, ownedKey, toggleOwned } from './checklist'

const medicham: OwnedSpecies = { speciesId: 'medicham', shadow: false }
const registeel: OwnedSpecies = { speciesId: 'registeel', shadow: false }
const shadowRegisteel: OwnedSpecies = { speciesId: 'registeel', shadow: true }

describe('toggleOwned', () => {
  it('ticks a species that is not on the list', () => {
    expect(toggleOwned([medicham], registeel)).toEqual([medicham, registeel])
  })

  it('unticks one that is, and a Shadow is a separate tick', () => {
    expect(toggleOwned([medicham, registeel, shadowRegisteel], { ...registeel })).toEqual([medicham, shadowRegisteel])
  })
})

describe('mergeOwned', () => {
  it('keeps every tick once, in the order first seen', () => {
    expect(mergeOwned([medicham, registeel], [registeel, shadowRegisteel])).toEqual([medicham, registeel, shadowRegisteel])
  })
})

describe('ownedKey', () => {
  it('tells a Shadow from its regular form', () => {
    expect(ownedKey(registeel)).not.toBe(ownedKey(shadowRegisteel))
  })
})

describe('latestSaver', () => {
  function deferredSaves() {
    const calls: { list: OwnedSpecies[]; resolve: () => void; reject: (e: Error) => void }[] = []
    const save = (list: OwnedSpecies[]) =>
      new Promise<void>((resolve, reject) => {
        calls.push({ list, resolve, reject })
      })
    return { calls, save }
  }
  const flush = () => new Promise((r) => setTimeout(r, 0))

  it('sends one save at a time and then only the newest list', async () => {
    const { calls, save } = deferredSaves()
    const saveLatest = latestSaver(save, () => {})

    saveLatest([medicham])
    saveLatest([medicham, registeel])
    saveLatest([medicham, registeel, shadowRegisteel])
    expect(calls).toHaveLength(1)

    calls[0].resolve()
    await flush()
    expect(calls.map((c) => c.list.length)).toEqual([1, 3])
  })

  it('reports saving, then the error a failed save gave', async () => {
    const { calls, save } = deferredSaves()
    const states: { saving: boolean; error: string | null }[] = []
    const saveLatest = latestSaver(save, (s) => states.push(s))

    saveLatest([medicham])
    calls[0].reject(new Error("Can't reach DexPresso."))
    await flush()

    expect(states).toEqual([
      { saving: true, error: null },
      { saving: false, error: "Can't reach DexPresso." },
    ])
  })
})

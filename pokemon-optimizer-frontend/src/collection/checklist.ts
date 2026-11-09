import type { OwnedSpecies } from '../types'

/** One key per checklist entry: a Shadow is its own tick. */
export function ownedKey(owned: OwnedSpecies): string {
  return owned.shadow ? `${owned.speciesId}/shadow` : owned.speciesId
}

/** Ticks it if it isn't there, unticks it if it is. */
export function toggleOwned(list: OwnedSpecies[], owned: OwnedSpecies): OwnedSpecies[] {
  const key = ownedKey(owned)
  return list.some((o) => ownedKey(o) === key) ? list.filter((o) => ownedKey(o) !== key) : [...list, owned]
}

/** Everything in either list, each once, in the order first seen. */
export function mergeOwned(a: OwnedSpecies[], b: OwnedSpecies[]): OwnedSpecies[] {
  const seen = new Map<string, OwnedSpecies>()
  for (const owned of [...a, ...b]) if (!seen.has(ownedKey(owned))) seen.set(ownedKey(owned), owned)
  return [...seen.values()]
}

/**
 * Saves the checklist without racing itself: one save at a time, and while one is out only the newest list
 * waits, so quick ticks end in one more save of the final list instead of a pile of out-of-order PUTs.
 */
export function latestSaver(
  save: (list: OwnedSpecies[]) => Promise<unknown>,
  onState: (state: { saving: boolean; error: string | null }) => void,
): (list: OwnedSpecies[]) => void {
  let inFlight = false
  let waiting: OwnedSpecies[] | null = null

  async function send(list: OwnedSpecies[]) {
    inFlight = true
    onState({ saving: true, error: null })
    let error: string | null = null
    try {
      await save(list)
    } catch (e: unknown) {
      error = e instanceof Error ? e.message : String(e)
    }
    inFlight = false
    if (waiting) {
      const next = waiting
      waiting = null
      send(next)
    } else {
      onState({ saving: false, error })
    }
  }

  return (list) => {
    if (inFlight) waiting = list
    else send(list)
  }
}

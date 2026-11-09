import type { CaughtPokemon, CaughtPokemonRequest } from '../types'

type Identity = Pick<CaughtPokemonRequest, 'speciesId' | 'atkIv' | 'defIv' | 'staIv' | 'shadow'>

/**
 * Same species, IVs and shadow means the same Pokémon (two identical ones are very rare and couldn't share a
 * team anyway). The backend's batch save uses the same rule.
 */
export function sameness(p: Identity): string {
  return `${p.speciesId}|${p.atkIv}|${p.defIv}|${p.staIv}|${p.shadow}`
}

/** Same cap as the server's (CaughtPokemonController): the Team Builder takes at most this many at once. */
export const MAX_COLLECTION = 10_000

export interface MergeResult {
  list: CaughtPokemon[]
  added: number
  /** already there, but powered up since: kept once, at the higher level */
  raised: number
  /** already there at the same or a higher level */
  skipped: number
}

/**
 * Adds what's new, raises the level of a powered-up duplicate, and skips the rest. Throws, changing nothing,
 * when the new ones wouldn't fit under MAX_COLLECTION.
 */
export function mergeInto(list: CaughtPokemon[], incoming: CaughtPokemonRequest[], nextId: () => number): MergeResult {
  const byKey = new Map<string, CaughtPokemon>()
  for (const p of list) if (!byKey.has(sameness(p))) byKey.set(sameness(p), p)

  const raisedKeys = new Set<string>()
  let added = 0
  let skipped = 0
  for (const request of incoming) {
    const key = sameness(request)
    const match = byKey.get(key)
    if (!match) {
      byKey.set(key, { ...request, id: nextId() })
      added++
    } else if (request.level !== null && (match.level === null || request.level > match.level)) {
      byKey.set(key, { ...match, level: request.level })
      raisedKeys.add(key)
    } else {
      skipped++
    }
  }
  const room = Math.max(0, MAX_COLLECTION - list.length)
  if (added > room) {
    throw new Error(
      room === 0
        ? 'Your Collection is full (10,000 Pokémon). Remove some to add more.'
        : `A Collection holds up to 10,000 Pokémon, and yours has room for ${room} more.`,
    )
  }
  return { list: [...byKey.values()], added, raised: raisedKeys.size, skipped }
}

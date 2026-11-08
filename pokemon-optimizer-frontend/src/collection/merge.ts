import type { CaughtPokemon, CaughtPokemonRequest } from '../types'

type Identity = Pick<CaughtPokemonRequest, 'speciesId' | 'atkIv' | 'defIv' | 'staIv' | 'shadow'>

/**
 * Same species, IVs and shadow means the same Pokémon (two identical ones are very rare and couldn't share a
 * team anyway). The backend's batch save uses the same rule.
 */
export function sameness(p: Identity): string {
  return `${p.speciesId}|${p.atkIv}|${p.defIv}|${p.staIv}|${p.shadow}`
}

export interface MergeResult {
  list: CaughtPokemon[]
  added: number
  /** already there, but powered up since: kept once, at the higher level */
  raised: number
  /** already there at the same or a higher level */
  skipped: number
}

/** Adds what's new, raises the level of a powered-up duplicate, and skips the rest. */
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
    } else if (request.level > match.level) {
      byKey.set(key, { ...match, level: request.level })
      raisedKeys.add(key)
    } else {
      skipped++
    }
  }
  return { list: [...byKey.values()], added, raised: raisedKeys.size, skipped }
}

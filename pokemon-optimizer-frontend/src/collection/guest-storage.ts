import type { CaughtPokemon } from '../types'

// A guest's Pokémon live only in this browser. Storage can be missing or blocked (private windows,
// cleared site data), so every access is guarded and the page still works with an empty list.
const KEY = 'dexpresso.guestCollection.v1'

export function loadGuestCollection(): CaughtPokemon[] {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter(isCaughtPokemon) : []
  } catch {
    return []
  }
}

/** False when the browser wouldn't keep it (storage blocked or full). */
export function saveGuestCollection(list: CaughtPokemon[]): boolean {
  try {
    localStorage.setItem(KEY, JSON.stringify(list))
    return true
  } catch {
    return false
  }
}

function isCaughtPokemon(value: unknown): value is CaughtPokemon {
  if (typeof value !== 'object' || value === null) return false
  const p = value as Record<string, unknown>
  return (
    typeof p.id === 'number' &&
    typeof p.speciesId === 'string' &&
    typeof p.atkIv === 'number' &&
    typeof p.defIv === 'number' &&
    typeof p.staIv === 'number' &&
    typeof p.level === 'number' &&
    typeof p.fastMove === 'string' &&
    Array.isArray(p.chargedMoves) &&
    typeof p.shadow === 'boolean'
  )
}

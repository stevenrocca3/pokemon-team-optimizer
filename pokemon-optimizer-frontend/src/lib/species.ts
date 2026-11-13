import type { SpeciesOption } from '../types'

/**
 * The 96px game sprite by Pokédex number (forms reuse their base species'), about 1 KB each. PokéAPI's sprites,
 * served by the site itself from public/sprites (tools/sprites/fetch.py) and cached for a month.
 */
export function iconUrl(dex: number): string {
  return `/sprites/${dex}.png`
}

export function formatLevel(level: number): string {
  return Number.isInteger(level) ? String(level) : level.toFixed(1)
}

export function formatDex(dex: number): string {
  return `#${String(dex).padStart(4, '0')}`
}

/** Dex order; "Stunfisk" sorts before "Stunfisk (Galarian)" because it is a prefix. */
export function sortByDex(list: SpeciesOption[]): SpeciesOption[] {
  return [...list].sort((a, b) => a.dex - b.dex || a.name.localeCompare(b.name))
}

/** Ranks name-prefix matches above substring matches; also matches dex numbers. */
export function searchSpecies(list: SpeciesOption[], query: string, limit = 8): SpeciesOption[] {
  const q = query.trim().toLowerCase().replace(/^#/, '')
  if (!q) return []

  const scored: { species: SpeciesOption; score: number }[] = []
  for (const species of list) {
    const name = species.name.toLowerCase()
    let score = -1
    if (String(species.dex) === q) score = 0
    else if (name.startsWith(q)) score = 1
    else if (name.includes(q)) score = 2
    if (score >= 0) scored.push({ species, score })
  }
  // list is already in dex order, and Array.sort is stable
  return scored.sort((a, b) => a.score - b.score).slice(0, limit).map((s) => s.species)
}

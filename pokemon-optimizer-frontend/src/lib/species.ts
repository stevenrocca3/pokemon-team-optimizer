import type { SpeciesOption } from '../types'

/** Pokédex number from an ID like "706-hisuian" → 706. */
export function dexNumberOf(id: string): number {
  return Number.parseInt(id, 10)
}

/** Official artwork for the base form. Forms reuse their base species' art. */
export function artworkUrl(id: string): string {
  return `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/${dexNumberOf(id)}.png`
}

export function displayName(species: SpeciesOption): string {
  return species.form ? `${species.name} (${species.form})` : species.name
}

export function formatDex(id: string): string {
  return `#${String(dexNumberOf(id)).padStart(4, '0')}`
}

/** Dex order, base form before its variants. */
export function sortByDex(list: SpeciesOption[]): SpeciesOption[] {
  return [...list].sort((a, b) => {
    const byDex = dexNumberOf(a.id) - dexNumberOf(b.id)
    if (byDex !== 0) return byDex
    if (a.form === '') return -1
    if (b.form === '') return 1
    return a.form.localeCompare(b.form)
  })
}

/** Ranks name-prefix matches above substring matches; also matches dex numbers. */
export function searchSpecies(list: SpeciesOption[], query: string, limit = 8): SpeciesOption[] {
  const q = query.trim().toLowerCase().replace(/^#/, '')
  if (!q) return []

  const scored: { species: SpeciesOption; score: number }[] = []
  for (const species of list) {
    const name = species.name.toLowerCase()
    const full = `${species.form} ${species.name}`.toLowerCase()
    let score = -1
    if (String(dexNumberOf(species.id)) === q) score = 0
    else if (name.startsWith(q)) score = 1
    else if (full.startsWith(q) || name.includes(q)) score = 2
    else if (full.includes(q)) score = 3
    if (score >= 0) scored.push({ species, score })
  }
  // list is already in dex order, and Array.sort is stable
  return scored.sort((a, b) => a.score - b.score).slice(0, limit).map((s) => s.species)
}

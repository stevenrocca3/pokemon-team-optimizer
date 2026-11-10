import type { SpeciesOption } from '../../types'

/** Lower case letters and digits only, accents dropped: "Flabébé" → "flabebe", "Mr. Mime" → "mrmime". */
function normalise(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '')
}

/** "Meowth (Galarian)" → "meowth": the screen shows the species name without its form. */
const baseName = (name: string) => normalise(name.replace(/\s*\(.*\)\s*$/, ''))

/**
 * The species (every form of it) whose name the OCR'd text starts with. OCR often adds junk after the name, so it's a
 * prefix match, and the longest name wins: "Mewtwo" is Mewtwo, not Mew. A nickname matches nothing.
 */
export function speciesForName(text: string, species: SpeciesOption[]): SpeciesOption[] {
  const read = normalise(text)
  let best = 0
  let found: SpeciesOption[] = []
  for (const s of species) {
    const base = baseName(s.name)
    if (base.length < 3 || !read.startsWith(base) || base.length < best) continue
    if (base.length > best) {
      best = base.length
      found = []
    }
    found.push(s)
  }
  return found
}

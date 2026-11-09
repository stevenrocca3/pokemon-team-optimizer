import type { SiteStats } from '../types'

/** One number for the home page's proof strip: `value` then `suffix` ("12,000+", "4.6/5"), then `label`. */
export interface ProofStat {
  value: number
  suffix: string
  label: string
  decimals?: number
}

// Below these a number undersells the site, so it isn't shown at all
const MIN_TEAMS = 1_000
const MIN_POKEMON = 5_000
const MIN_TRAINERS = 500
const MIN_RATINGS = 25
const MIN_AVERAGE = 4

/** Rounded down to two significant figures, so "12,000+" is always true. */
export function niceFloor(n: number): number {
  if (n < 100) return n
  const step = 10 ** (Math.floor(Math.log10(n)) - 1)
  return Math.floor(n / step) * step
}

/** The totals worth showing, biggest proof first. Every one is a floor: guests aren't counted in Pokémon or players. */
export function proofStats(stats: SiteStats): ProofStat[] {
  const shown: ProofStat[] = []
  if (stats.teamsBuilt >= MIN_TEAMS) shown.push({ value: niceFloor(stats.teamsBuilt), suffix: '+', label: 'teams built' })
  const pokemon = stats.pokemonSaved + stats.speciesTicked
  if (pokemon >= MIN_POKEMON) shown.push({ value: niceFloor(pokemon), suffix: '+', label: 'Pokémon saved' })
  if (stats.trainers >= MIN_TRAINERS) shown.push({ value: niceFloor(stats.trainers), suffix: '+', label: 'players signed in' })
  const { total, average } = stats.ratings
  if (total >= MIN_RATINGS && average >= MIN_AVERAGE) {
    // rounded down too: 4.64 shows as 4.6, never 4.7
    shown.push({ value: Math.floor(average * 10) / 10, suffix: '/5', label: `from ${total} ratings`, decimals: 1 })
  }
  return shown
}

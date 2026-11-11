import type { CaughtPokemon, TeamBuilderLeague } from '../types'

export const MAX_COMPARED = 6

/** The copies to compare: ones that have moves, best stat product rank for the League first, at most six. */
export function pickCopies(copies: CaughtPokemon[], rankOf: (p: CaughtPokemon) => number | undefined): CaughtPokemon[] {
  return copies
    .filter((p) => p.fastMove && p.chargedMoves.length > 0)
    .map((p) => ({ p, rank: rankOf(p) ?? Number.MAX_SAFE_INTEGER }))
    .sort((a, b) => a.rank - b.rank)
    .slice(0, MAX_COMPARED)
    .map(({ p }) => p)
}

/** "0/15/15", plus "Shadow" when it is one. */
export function copyLabel(p: { atkIv: number; defIv: number; staIv: number; shadow: boolean }): string {
  const ivs = `${p.atkIv}/${p.defIv}/${p.staIv}`
  return p.shadow ? `Shadow ${ivs}` : ivs
}

export function shieldsLabel(shields: number): string {
  if (shields === 0) return 'no shields'
  return `${shields} shield${shields === 1 ? '' : 's'} each`
}

/** Who wins a flipped matchup, in words. */
export function winnersLabel(winners: string[]): string {
  if (winners.length === 1) return `only ${winners[0]} wins`
  return `${winners.slice(0, -1).join(', ')} and ${winners[winners.length - 1]} win`
}

/** A species the player has two or more battle-ready copies of: a "Which One to Build" question (#118). */
export interface BuildCandidate {
  speciesId: string
  copies: number
}

/** Species with 2+ copies that have moves, most copies first. */
export function buildCandidates(list: CaughtPokemon[]): BuildCandidate[] {
  const counts = new Map<string, number>()
  for (const p of list) {
    if (p.fastMove && p.chargedMoves.length > 0) counts.set(p.speciesId, (counts.get(p.speciesId) ?? 0) + 1)
  }
  return [...counts]
    .filter(([, copies]) => copies >= 2)
    .map(([speciesId, copies]) => ({ speciesId, copies }))
    .sort((a, b) => b.copies - a.copies || a.speciesId.localeCompare(b.speciesId))
}

/** How many percentage points more of its meta battles the first wins than the second, to one decimal. */
export function winRateEdge(first: { wins: number; battles: number }, second: { wins: number; battles: number }): number {
  return Math.round((winRate(first) - winRate(second)) * 10) / 10
}

/** Share of its meta battles won, as a percentage. */
export function winRate(p: { wins: number; battles: number }): number {
  return p.battles === 0 ? 0 : (p.wins / p.battles) * 100
}

/** Where a species' "Which One to Build" comparison lives for a League. */
export function compareHref(speciesId: string, league: TeamBuilderLeague): string {
  return `/species/${speciesId}?league=${league.toLowerCase()}#compare`
}

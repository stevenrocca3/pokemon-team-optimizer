import type { CaughtPokemon } from '../types'

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

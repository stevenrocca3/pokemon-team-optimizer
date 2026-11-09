import type { MatchupRef } from '../types'

/** One opponent with every shield scenario it appears at, in first-seen order. */
export function groupByOpponent(matchups: MatchupRef[]): { opponent: string; shields: number[] }[] {
  const byOpponent = new Map<string, number[]>()
  for (const m of matchups) byOpponent.set(m.opponent, [...(byOpponent.get(m.opponent) ?? []), m.shields])
  return [...byOpponent].map(([opponent, shields]) => ({ opponent, shields }))
}

/** "0 and 1 shields", "all shields", "2 shields". */
export function shieldsList(shields: number[]): string {
  if (shields.length === 3) return 'all shields'
  return `${shields.join(' and ')} shield${shields.length === 1 && shields[0] === 1 ? '' : 's'}`
}

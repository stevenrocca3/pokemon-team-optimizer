export type LeagueKey = 'little' | 'great' | 'ultra' | 'master'

export interface League {
  key: LeagueKey
  label: string
  cpLabel: string
  hasCap: boolean
  cap: number
}

export const LEAGUES: League[] = [
  { key: 'little', label: 'Little League', cpLabel: '500 CP', hasCap: true, cap: 500 },
  { key: 'great', label: 'Great League', cpLabel: '1500 CP', hasCap: true, cap: 1500 },
  { key: 'ultra', label: 'Ultra League', cpLabel: '2500 CP', hasCap: true, cap: 2500 },
  { key: 'master', label: 'Master League', cpLabel: 'No cap', hasCap: false, cap: 0 },
]

export const DEFAULT_LEAGUE = LEAGUES.find((l) => l.key === 'great')!

export function leagueFromKey(key: string | null): League {
  return LEAGUES.find((l) => l.key === key) ?? DEFAULT_LEAGUE
}

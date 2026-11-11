import type { OwnedSpecies, TeamBuilderLeague, TurnLog } from '../types'

/** The team seen in an analysed battle, handed to the Team Builder through the router's state. */
export interface BattleTeam {
  names: string[]
  species: OwnedSpecies[]
  league: TeamBuilderLeague
}

/** A detected name as a PvPoke species ID: "Weezing (Galarian)" -> "weezing_galarian", "Mr. Mime" -> "mr_mime". */
export function speciesIdFor(name: string): string {
  return name
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
}

/** the league your Pokémon's CP puts the battle in (the turn_log's cp is keyed "user:Name" / "opp:Name") */
export function leagueFor(cp: TurnLog['cp']): TeamBuilderLeague {
  const yours = Object.entries(cp)
    .filter(([key]) => key.startsWith('user:'))
    .map(([, value]) => value)
  const top = Math.max(0, ...yours)
  return top <= 1500 ? 'GREAT' : top <= 2500 ? 'ULTRA' : 'MASTER'
}

export const LEAGUE_NAME: Record<TeamBuilderLeague, string> = {
  GREAT: 'Great League',
  ULTRA: 'Ultra League',
  MASTER: 'Master League',
}

export function battleTeam(log: TurnLog): BattleTeam {
  const names = log.user_team.slice(0, 3)
  return {
    names,
    species: names.map((n) => ({ speciesId: speciesIdFor(n), shadow: false })),
    league: leagueFor(log.cp),
  }
}

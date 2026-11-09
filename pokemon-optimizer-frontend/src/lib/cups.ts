import type { CupSummary, TeamBuilderLeague } from '../types'

export const LEAGUE_NAMES: Record<TeamBuilderLeague, string> = { GREAT: 'Great League', ULTRA: 'Ultra League', MASTER: 'Master League' }

/** The live Cups in one League, by name. */
export function cupsFor(cups: CupSummary[], league: TeamBuilderLeague): CupSummary[] {
  return cups.filter((c) => c.league === league).sort((a, b) => a.title.localeCompare(b.title))
}

/** What a Team Builder run is for: the Cup's name, or the League's for the open League. */
export function formatName(league: TeamBuilderLeague, cup: CupSummary | null): string {
  return cup ? cup.title : LEAGUE_NAMES[league]
}

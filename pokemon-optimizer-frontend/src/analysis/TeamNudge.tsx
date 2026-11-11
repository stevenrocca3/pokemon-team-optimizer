import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { buildTeams } from '../api'
import { SwapSuggestions } from '../components/SwapSuggestions'
import type { AnalysisResult, TeamResult } from '../types'
import { battleTeam, LEAGUE_NAME } from './team-nudge'

const WEAK = 0.5 // a team winning under half its meta battles gets the warning

/**
 * The bottom of the review: the team you brought, scored by the Team Builder against the current meta. Under 50%
 * gets a warning card; either way a team can be taken into the Team Builder in one click. A full team that holds up
 * gets nothing (the review is about execution).
 */
export function TeamNudge({ result }: { result: AnalysisResult }) {
  const team = useMemo(() => battleTeam(result.turn_log), [result])
  const navigate = useNavigate()
  const full = team.species.length === 3
  const [scored, setScored] = useState<TeamResult | null>(null)

  useEffect(() => {
    if (!full) return
    const controller = new AbortController()
    buildTeams({ league: team.league, pokemon: [], species: team.species, cup: null }, controller.signal)
      .then((built) => {
        const best = built.teams[0]
        if (best) setScored(best)
      })
      .catch(() => {}) // no score, no card
    return () => controller.abort()
  }, [full, team])

  const importTeam = () => navigate('/team-builder', { state: { battleTeam: team } })
  const button = (
    <button type="button" onClick={importTeam} className="btn btn-primary">
      Import Team to Dexpresso Team Builder
    </button>
  )

  if (!full && team.names.length > 0) {
    const seen =
      team.names.length === 1 ? team.names[0] : `${team.names.slice(0, -1).join(', ')} and ${team.names.at(-1)}`
    return (
      <section aria-labelledby="team-nudge" className="card flex flex-wrap items-center justify-between gap-4 p-5">
        <div className="max-w-xl">
          <h2 id="team-nudge" className="font-display text-lg text-ink">
            Your team in the Team Builder
          </h2>
          <p className="mt-1 text-sm text-ink-soft">
            Only {seen} came out, so the team couldn’t be scored. Import {team.names.length === 1 ? 'it' : 'them'} and
            the Team Builder finds the best partners from your Pokémon.
          </p>
        </div>
        {button}
      </section>
    )
  }

  if (scored === null || scored.winRate.rate >= WEAK) return null
  const rate = scored.winRate.rate
  return (
    <section aria-labelledby="team-nudge" className="card animate-rise p-5 ring-2 ring-tier-critical/60">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex max-w-xl gap-3">
          <span aria-hidden className="text-2xl leading-none">
            ⚠️
          </span>
          <div>
            <h2 id="team-nudge" className="font-display text-lg text-tier-critical">
              Critical Team Vulnerability Detected
            </h2>
            <p className="mt-1 text-sm text-ink-soft">
              Your execution was impacted by team synergy. Your team ({team.names.join(', ')}){' '}
              <span className="font-semibold text-ink">
                won {Math.round(rate * 100)}% of its battles against the current {LEAGUE_NAME[team.league]} meta
              </span>
              . These swaps from the meta fix the most, or import it and the Team Builder works from your own Pokémon.
            </p>
          </div>
        </div>
        {button}
      </div>
      <SwapSuggestions suggestions={scored.suggestions ?? []} members={scored.members} />
    </section>
  )
}

import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { compareCopies } from '../api'
import { useCollection } from '../collection/collection-context'
import { compareHref, pickCopies } from '../lib/compare'
import { useSpecies } from '../species/species-context'
import type { AnalysisResult } from '../types'
import { battleTeam } from './team-nudge'

type Nudge = { speciesId: string; copies: number; moreWins: number }

/**
 * The match review's "Which One to Build" nudge (#118): if you have two or more copies of a Pokémon you battled with,
 * the comparison runs here and the card says how many more meta matchups the better copy wins.
 */
export function BuildNudge({ result }: { result: AnalysisResult }) {
  const collection = useCollection()
  const species = useSpecies()
  const team = useMemo(() => battleTeam(result.turn_log), [result])
  const [nudge, setNudge] = useState<Nudge | null>(null)

  // the first Pokémon from the battle you have 2+ battle-ready copies of
  const list = collection.status === 'ready' ? collection.list : null
  const copies = useMemo(() => {
    if (!list) return []
    for (const { speciesId } of team.species) {
      const mine = pickCopies(
        list.filter((p) => p.speciesId === speciesId),
        () => undefined,
      )
      if (mine.length >= 2) return mine
    }
    return []
  }, [list, team])

  useEffect(() => {
    if (copies.length < 2) return
    const controller = new AbortController()
    const pokemon = copies.map(({ speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow }) => ({
      speciesId,
      atkIv,
      defIv,
      staIv,
      level,
      fastMove,
      chargedMoves,
      shadow,
    }))
    compareCopies(team.league, pokemon, controller.signal)
      .then((compared) => {
        const [first, second] = compared.pokemon.filter((p) => p.problem === null)
        if (first && second)
          setNudge({ speciesId: copies[0].speciesId, copies: copies.length, moreWins: first.wins - second.wins })
      })
      .catch(() => {}) // no comparison, no card
    return () => controller.abort()
  }, [copies, team])

  if (!nudge || copies[0]?.speciesId !== nudge.speciesId) return null
  const name = (species.status === 'ready' ? species.byId.get(nudge.speciesId)?.name : undefined) ?? nudge.speciesId
  return (
    <section
      aria-labelledby="build-nudge"
      className="card animate-rise flex flex-wrap items-center justify-between gap-4 p-5"
    >
      <div className="max-w-xl">
        <h2 id="build-nudge" className="text-xs font-bold tracking-wider text-brand uppercase">
          Which One to Build
        </h2>
        <p className="mt-1 text-ink">
          {nudge.moreWins > 0
            ? `You have ${nudge.copies} ${name} in your box: see which one wins ${nudge.moreWins} more meta ${nudge.moreWins === 1 ? 'matchup' : 'matchups'}.`
            : `You have ${nudge.copies} ${name} in your box, and they win the same meta matchups: power up whichever costs less dust.`}
        </p>
      </div>
      <Link to={compareHref(nudge.speciesId, team.league)} className="btn btn-primary">
        Compare your {name}
      </Link>
    </section>
  )
}

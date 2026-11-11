import { Link } from 'react-router'
import { useBuildCandidates } from '../collection/use-build-candidates'
import { compareHref } from '../lib/compare'
import { useSpecies } from '../species/species-context'
import type { TeamBuilderLeague } from '../types'
import { PokemonArt } from './PokemonArt'

const SHOWN = 4

/**
 * The Team Builder's "Which One to Build" callout (#118): the species you have two or more of, each a link to its
 * comparison for this League. With none yet it still says what the feature does, so players know it's there.
 */
export function BuildCallout({ league }: { league: TeamBuilderLeague }) {
  const candidates = useBuildCandidates()
  const species = useSpecies()
  const nameOf = (id: string) => (species.status === 'ready' ? species.byId.get(id)?.name : undefined) ?? id
  const dexOf = (id: string) => (species.status === 'ready' ? species.byId.get(id)?.dex : undefined)
  const [first] = candidates

  return (
    <section
      aria-labelledby="build-callout"
      className="mb-6 flex flex-wrap items-center gap-x-5 gap-y-3 rounded-2xl bg-brand/10 px-4 py-3 ring-1 ring-brand/40 sm:px-5"
    >
      <div className="min-w-0 flex-1">
        <h2 id="build-callout" className="text-xs font-bold tracking-wider text-brand uppercase">
          Which One to Build
        </h2>
        <p className="mt-0.5 text-sm text-ink">
          {first ? (
            <>
              You have {first.copies} {nameOf(first.speciesId)}
              {candidates.length > 1 && ` and ${candidates.length - 1} more you've caught twice`}. See which copy wins
              more before you spend the dust.
            </>
          ) : (
            <>Caught the same Pokémon twice? Which One to Build battles your copies and tells you which wins more.</>
          )}
        </p>
      </div>
      {first ? (
        <ul className="flex flex-wrap gap-2">
          {candidates.slice(0, SHOWN).map((c) => (
            <li key={c.speciesId}>
              <Link
                to={compareHref(c.speciesId, league)}
                className="flex h-9 items-center gap-1.5 rounded-full bg-raised pr-3 pl-1 text-sm font-semibold text-ink ring-1 ring-line-strong transition hover:bg-raised-strong hover:ring-brand/60"
              >
                <PokemonArt icon dex={dexOf(c.speciesId)} alt="" className="h-7 w-7" />
                {nameOf(c.speciesId)}
                <span className="text-xs tabular-nums text-subtle">×{c.copies}</span>
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <Link to="/build" className="btn btn-secondary btn-sm">
          How it works
        </Link>
      )}
    </section>
  )
}

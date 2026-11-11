import { useState } from 'react'
import { Link } from 'react-router'
import { PageHeader } from '../components/PageHeader'
import { PokemonArt } from '../components/PokemonArt'
import { useBuildCandidates } from '../collection/use-build-candidates'
import { useCollection } from '../collection/collection-context'
import { compareHref } from '../lib/compare'
import { usePageTitle } from '../lib/page-title'
import { LEAGUE_NAMES } from '../lib/cups'
import { useSpecies } from '../species/species-context'
import type { TeamBuilderLeague } from '../types'

const LEAGUES: { key: TeamBuilderLeague; label: string }[] = [
  { key: 'GREAT', label: 'Great' },
  { key: 'ULTRA', label: 'Ultra' },
  { key: 'MASTER', label: 'Master' },
]

/**
 * /build (#118): every species you have two or more battle-ready copies of, each a link to its "Which One to Build"
 * comparison for the League picked here.
 */
export function BuildPage() {
  usePageTitle('Which One to Build')
  const collection = useCollection()
  const candidates = useBuildCandidates()
  const species = useSpecies()
  const [league, setLeague] = useState<TeamBuilderLeague>('GREAT')
  const option = (id: string) => (species.status === 'ready' ? species.byId.get(id) : undefined)

  return (
    <div className="mx-auto max-w-5xl px-4 pb-16">
      <PageHeader eyebrow="Which One to Build" title="Which Copy Gets the Dust?">
        Each copy you have, powered to its best level for the League, battled against the meta at 0, 1 and 2 shields.
        The one that wins more is the one to build.
      </PageHeader>

      <div
        role="tablist"
        aria-label="League"
        className="mb-5 inline-grid grid-cols-3 gap-1 rounded-xl bg-raised p-1 ring-1 ring-line"
      >
        {LEAGUES.map((l) => (
          <button
            key={l.key}
            type="button"
            role="tab"
            aria-selected={l.key === league}
            onClick={() => setLeague(l.key)}
            className={`h-9 rounded-lg px-4 text-sm font-bold transition ${
              l.key === league
                ? 'bg-brand text-on-brand shadow inset-shadow-[0_3px_0] inset-shadow-creamer/60'
                : 'text-ink-soft hover:bg-raised-strong hover:text-ink'
            }`}
          >
            {l.label}
          </button>
        ))}
      </div>

      {collection.status !== 'ready' ? (
        <div className="card h-32 animate-pulse" aria-hidden="true" />
      ) : candidates.length === 0 ? (
        <div className="card p-6 text-ink-soft">
          <p>
            No Pokémon in your box twice yet. Add each copy's IVs and moves, and once you have two of the same one, it
            shows up here.
          </p>
          <Link to="/collection?view=exact" className="btn btn-primary mt-4">
            Add your copies
          </Link>
        </div>
      ) : (
        <>
          <h2 className="eyebrow">
            {candidates.length} to decide on for {LEAGUE_NAMES[league]}
          </h2>
          <ul className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {candidates.map((c, i) => {
              const name = option(c.speciesId)?.name ?? c.speciesId
              return (
                <li key={c.speciesId} className="animate-rise" style={{ animationDelay: `${Math.min(i, 8) * 50}ms` }}>
                  <Link
                    to={compareHref(c.speciesId, league)}
                    aria-label={`${name}: ${c.copies} copies`}
                    className="card flex items-center gap-4 p-4 transition hover:-translate-y-0.5 hover:ring-brand/60"
                  >
                    <PokemonArt dex={option(c.speciesId)?.dex} alt="" className="h-16 w-16 shrink-0" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-lg font-bold text-ink">{name}</span>
                      <span className="text-sm text-ink-soft">{c.copies} copies</span>
                    </span>
                    <span className="text-sm font-semibold text-brand" aria-hidden="true">
                      Compare →
                    </span>
                  </Link>
                </li>
              )
            })}
          </ul>
        </>
      )}
    </div>
  )
}

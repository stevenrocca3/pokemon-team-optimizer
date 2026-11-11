import { Link } from 'react-router'
import { useMetaName } from '../species/meta-name'
import { useSpecies } from '../species/species-context'
import type { Suggestion, SuggestionRole, TeamMember } from '../types'
import { PokemonArt } from './PokemonArt'

const ROLE: Record<SuggestionRole, { label: string; hint: string }> = {
  LEAD: { label: 'Lead', hint: 'Does best with shields up: starts battles' },
  SAFE_SWAP: { label: 'Safe Swap', hint: 'Does best at one shield each: comes in when you switch' },
  CLOSER: { label: 'Switcher/Closer', hint: 'Does best once shields are gone: finishes battles' },
}

/**
 * The Team Builder's swaps for a team winning under half its battles (#112): 5 meta Pokémon, best first, each with
 * the GBL role it plays best and the member it replaces. Nothing at all for a team without them.
 */
export function SwapSuggestions({
  suggestions,
  members,
  onAdd,
}: {
  suggestions: Suggestion[]
  members: TeamMember[]
  /** "Add to Box & Re-Analyze" (#114); without it the swaps are just shown (the match review) */
  onAdd?: (suggestion: Suggestion) => void
}) {
  const species = useSpecies()
  const metaName = useMetaName()
  if (suggestions.length === 0) return null
  const nameOf = (id: string) => (species.status === 'ready' ? species.byId.get(id)?.name : undefined) ?? id
  const replacedName = (s: Suggestion) =>
    nameOf(members.find((m) => m.index === s.replaces)?.speciesId ?? s.replacesSpeciesId)

  return (
    <div className="mt-5 rounded-xl bg-canvas/40 p-4 ring-1 ring-line">
      <p className="text-sm font-semibold text-ink">Under 50%: swaps that lift this team</p>
      <p className="mt-0.5 text-xs text-subtle">Meta Pokémon at PvPoke's IVs and moves, best first.</p>
      <ul aria-label="Swaps for this team" className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
        {suggestions.map((s, i) => {
          const option = species.status === 'ready' ? species.byId.get(s.speciesId) : undefined
          return (
            <li
              key={`${s.speciesId}-${s.shadow}`}
              className="animate-rise flex gap-2.5 rounded-lg bg-raised/60 p-2.5 ring-1 ring-line lg:flex-col"
              style={{ animationDelay: `${i * 70}ms` }}
            >
              <Link
                to={`/species/${s.speciesId}`}
                aria-label={option?.name ?? s.speciesId}
                className="h-12 w-12 shrink-0 rounded-md bg-canvas/60 p-1"
              >
                <PokemonArt dex={option?.dex} alt="" className="h-full w-full" />
              </Link>
              <div className="flex min-w-0 flex-1 flex-col text-xs">
                <div className="flex flex-wrap items-center gap-1">
                  <span className="truncate text-sm font-semibold text-ink">{option?.name ?? s.speciesId}</span>
                  {s.shadow && (
                    <span className="rounded bg-violet-500/25 px-1 py-0.5 text-[10px] font-bold uppercase text-violet-200">
                      Shadow
                    </span>
                  )}
                </div>
                <span
                  data-role={s.role}
                  title={ROLE[s.role].hint}
                  className="mt-1 self-start rounded bg-brand/15 px-1.5 py-0.5 text-[10px] font-bold uppercase text-brand ring-1 ring-brand/40"
                >
                  {ROLE[s.role].label}
                </span>
                <p className="mt-1 text-ink-soft">
                  for {replacedName(s)} ·{' '}
                  <span className="font-semibold tabular-nums text-brand">+{s.coverageGain.toFixed(1)} coverage</span>
                </p>
                {s.covers.length > 0 && (
                  <p className="mt-0.5 text-subtle">Helps against {s.covers.map(metaName).join(', ')}</p>
                )}
                {onAdd && <span aria-hidden className="h-2 shrink-0" />}
                {onAdd && (
                  <button
                    type="button"
                    onClick={() => onAdd(s)}
                    aria-label={`Add ${option?.name ?? s.speciesId} to Box & Re-Analyze`}
                    className="btn btn-secondary mt-auto h-auto min-h-8 w-full px-1.5 py-1.5 text-[11px] leading-tight"
                  >
                    + Add to Box & Re-Analyze
                  </button>
                )}
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

import { useEffect, useId, useMemo, useState } from 'react'
import { getTopMeta } from '../api'
import { useCollection } from '../collection/collection-context'
import { ownedKey, toggleOwned } from '../collection/checklist'
import { useSpecies } from '../species/species-context'
import { searchSpecies } from '../lib/species'
import type { MetaPick, OwnedSpecies, SpeciesOption, TeamBuilderLeague } from '../types'
import { PokemonArt } from './PokemonArt'
import { TypeBadge } from './TypeBadge'

const LEAGUES: { key: TeamBuilderLeague; label: string }[] = [
  { key: 'GREAT', label: 'Great' },
  { key: 'ULTRA', label: 'Ultra' },
  { key: 'MASTER', label: 'Master' },
]

type Meta = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; list: MetaPick[] }

/** How many top-ranked Pokémon each League's quick-select grid holds (#116). */
const TOP_SIZE: Record<TeamBuilderLeague, number> = { GREAT: 150, ULTRA: 150, MASTER: 50 }

// the rankings only change on a deploy: fetch each League once per visit
const metaCache = new Map<TeamBuilderLeague, Promise<MetaPick[]>>()
function loadMeta(league: TeamBuilderLeague): Promise<MetaPick[]> {
  let pending = metaCache.get(league)
  if (!pending) {
    pending = getTopMeta(league)
    pending.catch(() => metaCache.delete(league)) // let a retry fetch again
    metaCache.set(league, pending)
  }
  return pending
}

/**
 * The Collection as a checklist: a quick-select grid of each League's top-ranked Pokémon (PvPoke's top 150 for Great
 * and Ultra, 50 for Master) with a one-tap toggle each, plus a search for anything else. A tick says "I have one";
 * the Team Builder assumes good IVs for it.
 */
export function Checklist() {
  const collection = useCollection()
  const species = useSpecies()
  const [league, setLeague] = useState<TeamBuilderLeague>('GREAT')
  const [query, setQuery] = useState('')
  const [attempt, setAttempt] = useState(0)
  // the answer remembers which request it's for; anything else on screen is still loading
  const [answer, setAnswer] = useState<{ request: string; meta: Meta } | null>(null)
  const request = `${league}#${attempt}`
  const meta: Meta = answer?.request === request ? answer.meta : { status: 'loading' }
  const searchId = useId()

  useEffect(() => {
    let cancelled = false
    loadMeta(league)
      .then((list) => !cancelled && setAnswer({ request, meta: { status: 'ready', list } }))
      .catch(
        (error: unknown) =>
          !cancelled && setAnswer({ request, meta: { status: 'error', message: error instanceof Error ? error.message : String(error) } }),
      )
    return () => {
      cancelled = true
    }
  }, [league, request])

  const byId = species.status === 'ready' ? species.byId : null
  const ticked = useMemo(
    () => new Set(collection.status === 'ready' ? collection.checklist.map(ownedKey) : []),
    [collection],
  )
  // in rank order: the best are the ones most worth ticking first
  const metaSorted = meta.status === 'ready' ? meta.list : []

  if (collection.status !== 'ready') return null
  const { checklist, setChecklist, checklistSave } = collection
  const toggle = (owned: OwnedSpecies) => setChecklist(toggleOwned(checklist, owned))

  const metaKeys = new Set(metaSorted.map(ownedKey))
  const tickedInMeta = metaSorted.filter((o) => ticked.has(ownedKey(o))).length
  const elsewhere = checklist.filter((o) => !metaKeys.has(ownedKey(o)))
  const results = species.status === 'ready' ? searchSpecies(species.list, query, 24) : []
  const leagueLabel = LEAGUES.find((l) => l.key === league)!.label

  return (
    <section aria-label="Checklist">
      <div className="card p-4 sm:p-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <div role="tablist" aria-label="League" className="grid shrink-0 grid-cols-3 gap-1 rounded-xl bg-raised p-1 ring-1 ring-line">
            {LEAGUES.map((l) => (
              <button
                key={l.key}
                type="button"
                role="tab"
                aria-selected={l.key === league}
                onClick={() => {
                  setLeague(l.key)
                  setQuery('')
                }}
                className={`h-9 rounded-lg px-4 text-sm font-bold transition ${
                  l.key === league ? 'bg-brand text-on-brand shadow inset-shadow-[0_3px_0] inset-shadow-creamer/60' : 'text-ink-soft hover:bg-raised-strong hover:text-ink'
                }`}
              >
                {l.label}
              </button>
            ))}
          </div>
          <label htmlFor={searchId} className="sr-only">
            Find any Pokémon to tick
          </label>
          <input
            id={searchId}
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Find any other Pokémon…"
            autoComplete="off"
            className="field sm:max-w-xs"
          />
          <SaveState saving={checklistSave.saving} error={checklistSave.error} storage={collection.storage} />
        </div>
        <p className="mt-3 text-sm text-ink-soft">
          Tick the ones you have. The Team Builder gives each a good IV spread for the league and its best moves, so you
          don't need to type anything in.
        </p>
      </div>

      {query.trim() !== '' ? (
        <div className="mt-6">
          <h2 className="eyebrow">Search results</h2>
          {species.status !== 'ready' ? (
            <p className="mt-3 text-sm text-ink-soft">Loading Pokémon…</p>
          ) : results.length === 0 ? (
            <p className="mt-3 text-sm text-ink-soft">No Pokémon called "{query.trim()}".</p>
          ) : (
            <ul className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {results.flatMap((option) => {
                const forms: OwnedSpecies[] = [{ speciesId: option.id, shadow: false }]
                if (option.shadowEligible) forms.push({ speciesId: option.id, shadow: true })
                return forms.map((owned) => (
                  <CheckItem key={ownedKey(owned)} owned={owned} option={option} checked={ticked.has(ownedKey(owned))} onToggle={toggle} />
                ))
              })}
            </ul>
          )}
        </div>
      ) : (
        <div className="mt-6">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="eyebrow">
              {leagueLabel} League top {TOP_SIZE[league]}
            </h2>
            {meta.status === 'ready' && (
              <p className="text-sm tabular-nums text-subtle">
                {tickedInMeta} of {metaSorted.length} ticked
              </p>
            )}
          </div>

          {meta.status === 'loading' && (
            <ul className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5" aria-hidden="true">
              {Array.from({ length: 15 }, (_, i) => (
                <li key={i} className="h-[4.5rem] animate-pulse rounded-xl bg-surface ring-1 ring-line" />
              ))}
            </ul>
          )}
          {meta.status === 'error' && (
            <div role="alert" className="mt-3 flex flex-wrap items-center gap-3 text-sm text-ink-soft">
              Couldn't load the {leagueLabel} League list ({meta.message})
              <button type="button" onClick={() => setAttempt((n) => n + 1)} className="btn btn-secondary btn-sm">
                Try again
              </button>
            </div>
          )}
          {meta.status === 'ready' && (
            <ul
              aria-label={`${leagueLabel} League quick select`}
              className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5"
            >
              {metaSorted.map((pick, i) => (
                <QuickCard
                  key={ownedKey(pick)}
                  pick={pick}
                  rank={i + 1}
                  option={byId?.get(pick.speciesId)}
                  checked={ticked.has(ownedKey(pick))}
                  onToggle={toggle}
                />
              ))}
            </ul>
          )}
        </div>
      )}

      {elsewhere.length > 0 && (
        <div className="mt-8">
          <h2 className="eyebrow">
            Also ticked{query.trim() === '' ? `, not in the ${leagueLabel} League top ${TOP_SIZE[league]}` : ''}
          </h2>
          <ul className="mt-3 flex flex-wrap gap-2">
            {elsewhere.map((owned) => {
              const name = byId?.get(owned.speciesId)?.name ?? owned.speciesId
              const label = owned.shadow ? `Shadow ${name}` : name
              return (
                <li key={ownedKey(owned)}>
                  <button
                    type="button"
                    onClick={() => toggle(owned)}
                    aria-label={`Untick ${label}`}
                    className="flex h-9 items-center gap-2 rounded-full bg-raised pr-3 pl-1 text-sm text-ink ring-1 ring-line-strong hover:bg-raised-strong"
                  >
                    <PokemonArt icon dex={byId?.get(owned.speciesId)?.dex} alt="" className="h-7 w-7" />
                    {label}
                    <span aria-hidden="true" className="text-subtle">
                      ×
                    </span>
                  </button>
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </section>
  )
}

/**
 * One of the top-ranked Pokémon (#116), dense enough for 150 on a page: rank, icon, name, type badges, and a round
 * quick-add toggle. The whole card is the checkbox's label, so a tap anywhere ticks it.
 */
function QuickCard({
  pick,
  rank,
  option,
  checked,
  onToggle,
}: {
  pick: MetaPick
  rank: number
  option: SpeciesOption | undefined
  checked: boolean
  onToggle: (owned: OwnedSpecies) => void
}) {
  const name = option?.name ?? pick.speciesId
  const owned = { speciesId: pick.speciesId, shadow: pick.shadow }
  return (
    <li>
      <label
        className={`relative flex h-[4.5rem] cursor-pointer items-center gap-2 rounded-xl py-2 pr-9 pl-2 ring-1 transition has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-brand ${
          checked ? 'bg-brand/12 ring-brand/70' : 'bg-surface ring-line hover:bg-raised hover:ring-line-strong'
        }`}
      >
        <input
          type="checkbox"
          checked={checked}
          onChange={() => onToggle(owned)}
          aria-label={pick.shadow ? `Shadow ${name}` : name}
          className="peer sr-only"
        />
        <PokemonArt
          icon
          dex={option?.dex}
          alt=""
          className={`h-11 w-11 shrink-0 transition-[opacity,filter] duration-200 ${checked ? '' : 'opacity-50 grayscale'}`}
        />
        <span className="min-w-0 flex-1">
          <span className="flex items-baseline gap-1">
            <span className="text-[10px] tabular-nums text-subtle">#{rank}</span>
            <span className={`truncate text-sm font-semibold transition-colors duration-200 ${checked ? 'text-ink' : 'text-ink-soft'}`}>
              {name}
            </span>
          </span>
          <span className="mt-1 flex flex-wrap items-center gap-1">
            {pick.shadow && (
              <span className="rounded bg-violet-500/25 px-1 py-px text-[10px] leading-tight font-bold uppercase text-violet-200">
                Shadow
              </span>
            )}
            {pick.types.map((type) => (
              <TypeBadge key={type} type={type} />
            ))}
          </span>
        </span>
        {/* the quick-add toggle: + to add, ✓ once ticked */}
        <span
          aria-hidden="true"
          className={`absolute top-1/2 right-2 grid h-6 w-6 -translate-y-1/2 place-items-center rounded-full text-sm font-black transition ${
            checked ? 'scale-100 bg-brand text-on-brand' : 'bg-raised text-subtle ring-1 ring-line-strong'
          }`}
        >
          {checked ? '✓' : '+'}
        </span>
      </label>
    </li>
  )
}

function CheckItem({
  owned,
  option,
  checked,
  onToggle,
}: {
  owned: OwnedSpecies
  option: SpeciesOption | undefined
  checked: boolean
  onToggle: (owned: OwnedSpecies) => void
}) {
  const name = option?.name ?? owned.speciesId
  return (
    <li>
      <label
        className={`flex h-16 cursor-pointer items-center gap-3 rounded-xl px-3 ring-1 transition has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-brand ${
          checked ? 'bg-brand/12 ring-brand/70' : 'bg-surface ring-line hover:bg-raised hover:ring-line-strong'
        }`}
      >
        <input type="checkbox" checked={checked} onChange={() => onToggle(owned)} className="h-5 w-5 shrink-0 accent-brand" />
        {/* unticked ones are greyed out, so what you own stands out */}
        <PokemonArt
          icon
          dex={option?.dex}
          alt=""
          className={`h-12 w-12 shrink-0 transition-[opacity,filter] duration-200 ${checked ? '' : 'opacity-45 grayscale'}`}
        />
        <span className="min-w-0 flex-1">
          <span className={`block truncate font-semibold transition-colors duration-200 ${checked ? 'text-ink' : 'text-subtle'}`}>
            {name}
          </span>
          {owned.shadow && (
            <span className="mt-0.5 inline-block rounded bg-violet-500/25 px-1.5 py-0.5 text-[10px] font-bold uppercase text-violet-200">
              Shadow
            </span>
          )}
        </span>
      </label>
    </li>
  )
}

function SaveState({ saving, error, storage }: { saving: boolean; error: string | null; storage: 'account' | 'browser' }) {
  // a live region, so screen readers hear when a tick didn't save
  return (
    <p role="status" className="text-sm sm:ml-auto">
      {error ? (
        <span className="text-ink">Not saved: {error}</span>
      ) : saving ? (
        <span className="text-subtle">Saving…</span>
      ) : (
        <span className="text-subtle">{storage === 'account' ? 'Saved to your account' : 'Saved in this browser'}</span>
      )}
    </p>
  )
}

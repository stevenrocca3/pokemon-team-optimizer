import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { LOGIN_URL } from '../api'
import type { CaughtPokemon } from '../types'
import { useSpecies } from '../species/species-context'
import { PokemonArt } from '../components/PokemonArt'
import { IvTriple } from '../components/IvTriple'
import { QuickAdd } from '../components/QuickAdd'
import { formatDex, formatLevel } from '../lib/species'
import { useMoves } from '../moves/moves-context'
import { useCollection } from '../collection/collection-context'
import { loadGuestCollection, saveGuestCollection } from '../collection/guest-storage'
import { PageHeader } from '../components/PageHeader'
import { usePageTitle } from '../lib/page-title'
import { Checklist } from '../components/Checklist'

export function CollectionPage() {
  usePageTitle('Your Collection')
  const collection = useCollection()
  // the checklist is the quick way in; exact IVs are for players who want the closest answer
  const [params, setParams] = useSearchParams()
  const view = params.get('view') === 'exact' ? 'exact' : 'checklist'
  const ticked = collection.status === 'ready' ? collection.checklist.length : 0

  return (
    <div className="mx-auto max-w-6xl px-4 pb-16">
      <PageHeader
        eyebrow="Collection"
        title="Your Pokémon"
        actions={
          collection.status === 'ready' && (
            <Link to="/team-builder" className="btn btn-primary">
              Build teams
            </Link>
          )
        }
      >
        Tick the Pokémon you have and the Team Builder does the rest. Add exact IVs, or import a Poké Genie or Calcy IV
        export, when you want it to use your real ones.
      </PageHeader>

      {collection.status === 'ready' && collection.storage === 'browser' && (
        <p className="mb-6 flex flex-wrap items-center gap-x-2 rounded-xl bg-raised/80 px-4 py-3 text-sm text-ink-soft ring-1 ring-line">
          <span className="font-semibold text-ink">Saved in this browser only.</span>
          <span>
            {/* a plain <a>, not <Link>: login leaves the app for Spring and Google */}
            <a href={LOGIN_URL} className="text-brand underline">
              Log in
            </a>{' '}
            to keep them on every device. They'll move into your account.
          </span>
        </p>
      )}
      {collection.status === 'ready' && collection.storage === 'account' && <MoveGuestPokemonIn />}

      {collection.status === 'error' && (
        <div role="alert" className="rounded-2xl border border-red-900 bg-red-950/70 p-5 text-red-200">
          <p className="font-semibold">Couldn't load your Collection</p>
          <p className="mt-1 text-sm text-red-200">
            {collection.message}
          </p>
          <button type="button" onClick={() => window.location.reload()} className="btn btn-secondary btn-sm mt-4">
            Try again
          </button>
        </div>
      )}

      {collection.status === 'loading' && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }, (_, i) => (
            <div key={i} className="h-36 animate-pulse rounded-2xl bg-surface ring-1 ring-line" />
          ))}
        </div>
      )}

      {collection.status === 'ready' && (
        <div role="tablist" aria-label="Collection view" className="mb-6 inline-grid grid-cols-2 gap-1 rounded-xl bg-raised p-1 ring-1 ring-line">
          {(
            [
              ['checklist', 'Checklist', ticked],
              ['exact', 'Exact IVs', collection.list.length],
            ] as const
          ).map(([key, label, count]) => (
            <button
              key={key}
              type="button"
              role="tab"
              aria-selected={view === key}
              onClick={() => setParams(key === 'exact' ? { view: 'exact' } : {}, { replace: true })}
              className={`h-10 rounded-lg px-4 text-sm font-bold transition ${
                view === key ? 'bg-brand text-on-brand shadow' : 'text-ink-soft hover:bg-raised-strong hover:text-ink'
              }`}
            >
              {label}{' '}
              <span className={`tabular-nums ${view === key ? 'text-on-brand' : 'text-subtle'}`}>({count.toLocaleString()})</span>
            </button>
          ))}
        </div>
      )}

      {collection.status === 'ready' && view === 'checklist' && <Checklist />}

      {collection.status === 'ready' && view === 'exact' && (
        <>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <p className="max-w-2xl text-sm text-ink-soft">
              Pokémon with their real IVs. The Team Builder uses these instead of a checklist tick for the same species.
            </p>
            <Link to="/import" className="btn btn-secondary">
              Import a file
            </Link>
          </div>
          <QuickAdd />

          {collection.list.length === 0 ? (
            <EmptyShelf />
          ) : (
            <ul className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {collection.list.map((pokemon, i) => (
                <CaughtCard key={pokemon.id} pokemon={pokemon} delayMs={Math.min(i, 12) * 40} onRemove={() => collection.remove(pokemon.id)} />
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  )
}

/** Centred under the quick add: three steps to a first team, with empty cup slots standing in for the cards. */
function EmptyShelf() {
  const steps = [
    { title: 'Add three or more', body: 'Type a name, the appraisal IVs and the CP. The level works itself out.' },
    { title: 'Or import a file', body: 'A Poké Genie or Calcy IV export brings your whole box in at once.' },
    { title: 'Build a team', body: 'The Team Builder battles them against the meta and picks your best three.' },
  ]
  return (
    <section className="mx-auto mt-12 max-w-3xl text-center">
      <div className="flex justify-center gap-4" aria-hidden="true">
        {[0, 1, 2].map((i) => (
          <div
            key={i}
            className="grid h-20 w-20 animate-rise place-items-center rounded-2xl border-2 border-dashed border-line-strong/70 text-2xl text-subtle"
            style={{ animationDelay: `${i * 120}ms` }}
          >
            +
          </div>
        ))}
      </div>
      <h2 className="mt-6 font-display text-2xl font-black">Your shelf is empty</h2>
      <ol className="mt-6 grid gap-4 text-left sm:grid-cols-3">
        {steps.map((step, i) => (
          <li key={step.title} className="card p-5">
            <span className="text-xs font-black text-brand">0{i + 1}</span>
            <p className="mt-1 font-bold">{step.title}</p>
            <p className="mt-1 text-sm text-ink-soft">{step.body}</p>
          </li>
        ))}
      </ol>
      <div className="mt-6 flex flex-wrap justify-center gap-3">
        <Link to="/import" className="btn btn-secondary">
          Import a file
        </Link>
        <Link to="/team-builder" className="btn btn-ghost">
          See the Team Builder
        </Link>
      </div>
    </section>
  )
}

/** Pokémon added before logging in stay in the browser until the player moves them into their account. */
function MoveGuestPokemonIn() {
  const collection = useCollection()
  const [waiting, setWaiting] = useState(() => loadGuestCollection())
  const [status, setStatus] = useState<{ kind: 'idle' } | { kind: 'moving' } | { kind: 'error'; message: string }>({ kind: 'idle' })
  if (waiting.length === 0 || collection.status !== 'ready') return null

  async function moveIn() {
    if (collection.status !== 'ready') return
    setStatus({ kind: 'moving' })
    try {
      await collection.addAll(
        waiting.map(({ speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow }) => ({
          speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow,
        })),
      )
      saveGuestCollection([])
      setWaiting([])
    } catch (error: unknown) {
      setStatus({ kind: 'error', message: error instanceof Error ? error.message : String(error) })
    }
  }

  return (
    <div className="mb-6 flex flex-wrap items-center gap-3 rounded-xl bg-brand/10 p-4 ring-1 ring-brand/50">
      <p className="text-sm text-ink-soft">
        {waiting.length.toLocaleString()} Pokémon you added before logging in are still only in this browser.
      </p>
      <button
        onClick={moveIn}
        disabled={status.kind === 'moving'}
        className="btn btn-primary btn-sm"
      >
        {status.kind === 'moving' ? 'Moving…' : 'Move them to my account'}
      </button>
      {status.kind === 'error' && <span className="text-sm text-red-400">Couldn't move them ({status.message})</span>}
    </div>
  )
}

function CaughtCard({ pokemon, delayMs, onRemove }: { pokemon: CaughtPokemon; delayMs: number; onRemove: () => Promise<void> }) {
  const species = useSpecies()
  const option = species.status === 'ready' ? species.byId.get(pokemon.speciesId) : undefined
  const moveList = useMoves()
  const nameOf = (id: string) => (moveList.status === 'ready' ? moveList.byId.get(id)?.name : undefined) ?? id
  const [removing, setRemoving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function remove() {
    setRemoving(true)
    setError(null)
    try {
      await onRemove()
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e))
      setRemoving(false)
    }
  }

  return (
    <li className="card group flex animate-rise gap-4 p-4 transition hover:-translate-y-0.5 hover:ring-line-strong" style={{ animationDelay: `${delayMs}ms` }}>
      <Link to={`/species/${pokemon.speciesId}`} className="shrink-0" aria-label={option?.name ?? pokemon.speciesId}>
        <div className="h-24 w-24 rounded-xl bg-gradient-to-b from-raised to-canvas/40 p-2">
          <PokemonArt dex={option?.dex} alt="" className="h-full w-full transition group-hover:scale-110" />
        </div>
      </Link>
      <div className="min-w-0 flex-1">
        <div className="text-xs text-subtle">{option ? formatDex(option.dex) : ' '}</div>
        <Link to={`/species/${pokemon.speciesId}`} className="block truncate font-semibold hover:text-brand">
          {option?.name ?? pokemon.speciesId}
          {pokemon.shadow && (
            <span className="ml-2 rounded bg-violet-500/25 px-1.5 py-0.5 align-middle text-[10px] font-bold uppercase text-violet-200">
              Shadow
            </span>
          )}
        </Link>
        <div className="mt-2 flex items-end justify-between gap-3">
          <IvTriple atk={pokemon.atkIv} def={pokemon.defIv} sta={pokemon.staIv} />
          <div className="text-right">
            <div className="text-xs uppercase text-subtle">Level</div>
            {pokemon.level === null ? (
              <div className="text-sm text-subtle" title="Added without a CP. The Team Builder powers it to the best level for each league.">
                Not set
              </div>
            ) : (
              <div className="font-semibold tabular-nums">{formatLevel(pokemon.level)}</div>
            )}
          </div>
        </div>
        {/* Pokémon saved before movesets existed have no moves */}
        {pokemon.fastMove && (
          <p className="mt-2 truncate text-xs text-ink-soft" title={[pokemon.fastMove, ...pokemon.chargedMoves].map(nameOf).join(' · ')}>
            {nameOf(pokemon.fastMove)}
            <span className="text-subtle"> · </span>
            {pokemon.chargedMoves.map(nameOf).join(' / ')}
          </p>
        )}
        <div className="mt-3 flex items-center justify-between gap-2">
          <button
            onClick={remove}
            disabled={removing}
            className="text-xs text-muted underline-offset-2 hover:text-red-400 hover:underline disabled:opacity-50"
          >
            {removing ? 'Removing…' : 'Remove'}
          </button>
          {error && <span className="text-xs text-red-400">Couldn't remove ({error})</span>}
        </div>
      </div>
    </li>
  )
}

import { useState } from 'react'
import { Link } from 'react-router'
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

export function CollectionPage() {
  const collection = useCollection()

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="flex items-baseline gap-3 text-4xl font-black tracking-tight">
          Collection
          {collection.status === 'ready' && (
            <span className="text-base font-normal text-subtle">{collection.list.length.toLocaleString()} Pokémon</span>
          )}
        </h1>
        {collection.status === 'ready' && (
          <Link to="/import" className="text-sm font-semibold text-brand underline-offset-2 hover:underline">
            Import a Poké Genie or Calcy IV export
          </Link>
        )}
      </div>

      {collection.status === 'ready' && collection.storage === 'browser' && (
        <p className="mt-2 max-w-2xl text-sm text-ink-soft">
          Saved in this browser only.{' '}
          {/* a plain <a>, not <Link>: login leaves the app for Spring and Google */}
          <a href={LOGIN_URL} className="text-brand underline">
            Log in
          </a>{' '}
          to keep them on every device. They'll move into your account.
        </p>
      )}
      {collection.status === 'ready' && collection.storage === 'account' && <MoveGuestPokemonIn />}

      {collection.status === 'error' && (
        <div role="alert" className="mt-6 rounded-lg border border-red-900 bg-red-950/50 p-5 text-red-200">
          <p className="font-semibold">Couldn't load your Collection</p>
          <p className="mt-1 text-sm text-red-300/80">
            {collection.message}. Check that the Spring backend and the database are running.
          </p>
        </div>
      )}

      {collection.status === 'loading' && (
        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }, (_, i) => (
            <div key={i} className="h-32 animate-pulse rounded-lg bg-surface ring-1 ring-line" />
          ))}
        </div>
      )}

      {collection.status === 'ready' && (
        <>
          <div className="mt-6">
            <QuickAdd />
          </div>

          {collection.list.length === 0 ? (
            <p className="mt-6 max-w-xl text-sm text-subtle">
              Nothing here yet. Add Pokémon above with the IVs from their appraisal, or{' '}
              <Link to="/import" className="text-brand underline">
                import a Poké Genie or Calcy IV export
              </Link>
              . Three or more and the{' '}
              <Link to="/team-builder" className="text-brand underline">
                Team Builder
              </Link>{' '}
              can find your best team.
            </p>
          ) : (
            <ul className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {collection.list.map((pokemon) => (
                <CaughtCard key={pokemon.id} pokemon={pokemon} onRemove={() => collection.remove(pokemon.id)} />
              ))}
            </ul>
          )}
        </>
      )}
    </div>
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
    <div className="mt-4 flex max-w-2xl flex-wrap items-center gap-3 rounded-lg bg-brand/10 p-4 ring-1 ring-brand/40">
      <p className="text-sm text-ink-soft">
        {waiting.length.toLocaleString()} Pokémon you added before logging in are still only in this browser.
      </p>
      <button
        onClick={moveIn}
        disabled={status.kind === 'moving'}
        className="rounded-md bg-brand px-3 py-1.5 text-sm font-bold text-on-brand transition hover:brightness-110 disabled:opacity-50"
      >
        {status.kind === 'moving' ? 'Moving…' : 'Move them to my account'}
      </button>
      {status.kind === 'error' && <span className="text-sm text-red-400">Couldn't move them ({status.message})</span>}
    </div>
  )
}

function CaughtCard({ pokemon, onRemove }: { pokemon: CaughtPokemon; onRemove: () => Promise<void> }) {
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
    <li className="flex gap-4 rounded-lg bg-surface p-4 ring-1 ring-line">
      <Link to={`/species/${pokemon.speciesId}`} className="shrink-0" aria-label={option?.name ?? pokemon.speciesId}>
        <div className="h-20 w-20 rounded-md bg-raised/60 p-2">
          <PokemonArt dex={option?.dex} alt="" className="h-full w-full" />
        </div>
      </Link>
      <div className="min-w-0 flex-1">
        <div className="text-xs text-subtle">{option ? formatDex(option.dex) : ' '}</div>
        <Link to={`/species/${pokemon.speciesId}`} className="block truncate font-semibold hover:text-brand">
          {option?.name ?? pokemon.speciesId}
          {pokemon.shadow && (
            <span className="ml-2 rounded bg-violet-500/20 px-1.5 py-0.5 align-middle text-[10px] font-bold uppercase text-violet-300">
              Shadow
            </span>
          )}
        </Link>
        <div className="mt-2 flex items-end justify-between gap-3">
          <IvTriple atk={pokemon.atkIv} def={pokemon.defIv} sta={pokemon.staIv} />
          <div className="text-right">
            <div className="text-xs uppercase text-subtle">Level</div>
            <div className="font-semibold tabular-nums">{formatLevel(pokemon.level)}</div>
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

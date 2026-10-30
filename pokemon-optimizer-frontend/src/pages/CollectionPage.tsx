import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getCollection, LOGIN_URL, NotLoggedInError, removeFromCollection } from '../api'
import type { CaughtPokemon } from '../types'
import { useSpecies } from '../species/species-context'
import { PokemonArt } from '../components/PokemonArt'
import { IvTriple } from '../components/IvTriple'
import { SearchBar } from '../components/SearchBar'
import { formatDex, formatLevel } from '../lib/species'

type CollectionState = { status: 'loading' } | { status: 'logged-out' } | { status: 'error'; message: string } | { status: 'ready'; list: CaughtPokemon[] }

export function CollectionPage() {
  const [state, setState] = useState<CollectionState>({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    getCollection()
      .then((list) => {
        // newest first; IDs are assigned in insertion order
        if (!cancelled) setState({ status: 'ready', list: [...list].sort((a, b) => b.id - a.id) })
      })
      .catch((error: unknown) => {
        if (cancelled) return
        if (error instanceof NotLoggedInError) setState({ status: 'logged-out' })
        else setState({ status: 'error', message: error instanceof Error ? error.message : String(error) })
      })
    return () => {
      cancelled = true
    }
  }, [])

  function removed(id: number) {
    setState((s) => (s.status === 'ready' ? { status: 'ready', list: s.list.filter((p) => p.id !== id) } : s))
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <h1 className="flex items-baseline gap-3 text-4xl font-black tracking-tight">
        Collection
        {state.status === 'ready' && (
          <span className="text-base font-normal text-subtle">
            {state.list.length} Pokémon
          </span>
        )}
      </h1>

      {state.status === 'error' && (
        <div role="alert" className="mt-6 rounded-lg border border-red-900 bg-red-950/50 p-5 text-red-200">
          <p className="font-semibold">Couldn't load your Collection</p>
          <p className="mt-1 text-sm text-red-300/80">
            {state.message}. Check that the Spring backend and the database are running.
          </p>
        </div>
      )}

      {state.status === 'logged-out' && (
        <div className="mt-10 max-w-xl">
          <p className="text-ink-soft">Log in to see your Collection.</p>
          <p className="mt-1 text-sm text-subtle">Each Trainer keeps their own Pokémon.</p>
          {/* a plain <a>, not <Link>: login leaves the app for Spring and Google */}
          <a
            href={LOGIN_URL}
            className="mt-6 inline-block rounded-md bg-brand px-4 py-2 text-sm font-bold text-on-brand transition hover:brightness-110"
          >
            Log in with Google
          </a>
        </div>
      )}

      {state.status === 'loading' && (
        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }, (_, i) => (
            <div key={i} className="h-32 animate-pulse rounded-lg bg-surface ring-1 ring-line" />
          ))}
        </div>
      )}

      {state.status === 'ready' && state.list.length === 0 && (
        <div className="mt-10 max-w-xl">
          <p className="text-ink-soft">Your Collection is empty.</p>
          <p className="mt-1 text-sm text-subtle">
            Find a Pokémon, set its IVs and level, then press <span className="text-ink-soft">Add to Collection</span>.
          </p>
          <div className="mt-6">
            <SearchBar size="large" />
          </div>
        </div>
      )}

      {state.status === 'ready' && state.list.length > 0 && (
        <ul className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {state.list.map((pokemon) => (
            <CaughtCard key={pokemon.id} pokemon={pokemon} onRemoved={removed} />
          ))}
        </ul>
      )}
    </div>
  )
}

function CaughtCard({ pokemon, onRemoved }: { pokemon: CaughtPokemon; onRemoved: (id: number) => void }) {
  const species = useSpecies()
  const option = species.status === 'ready' ? species.byId.get(pokemon.speciesId) : undefined
  const [removing, setRemoving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function remove() {
    setRemoving(true)
    setError(null)
    try {
      await removeFromCollection(pokemon.id)
      onRemoved(pokemon.id)
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
        </Link>
        <div className="mt-2 flex items-end justify-between gap-3">
          <IvTriple atk={pokemon.atkIv} def={pokemon.defIv} sta={pokemon.staIv} />
          <div className="text-right">
            <div className="text-xs uppercase text-subtle">Level</div>
            <div className="font-semibold tabular-nums">{formatLevel(pokemon.level)}</div>
          </div>
        </div>
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

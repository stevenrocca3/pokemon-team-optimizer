import { Link } from 'react-router'
import { SearchBar } from '../components/SearchBar'
import { PokemonArt } from '../components/PokemonArt'
import { useSpecies } from '../species/species-context'
import { displayName, formatDex } from '../lib/species'

// Staples of GO Battle League — quick ways into the app
const POPULAR_IDS = ['308', '184', '379', '227', '334', '709', '108', '487']

const EXPLAINERS = [
  {
    title: 'Stat Product Rank',
    body: 'Attack × Defense × HP at the highest level under the league cap. Rank 1 is the bulkiest overall spread.',
  },
  {
    title: 'Mirror Rank',
    body: 'Spreads ordered by Attack. A better Mirror Rank wins more CMP ties when both sides throw a charged move together.',
  },
  {
    title: 'League caps',
    body: 'Great League caps at 1500 CP and Ultra at 2500, so low Attack IVs often let a Pokémon level higher and end up stronger.',
  },
]

export function HomePage() {
  const species = useSpecies()

  return (
    <div>
      <section className="border-b border-zinc-800 bg-gradient-to-b from-zinc-900 to-zinc-950">
        <div className="mx-auto max-w-3xl px-4 py-16 text-center sm:py-24">
          <p className="mb-3 text-sm font-semibold uppercase tracking-widest text-brand">Pokémon GO PvP</p>
          <h1 className="text-4xl font-black tracking-tight sm:text-5xl">Find the best IVs for every league</h1>
          <p className="mx-auto mt-4 max-w-xl text-zinc-400">
            Every one of a species' 4,096 IV spreads, ranked for Great, Ultra and Master League.
          </p>
          <div className="mx-auto mt-8 max-w-2xl text-left">
            <SearchBar size="large" autoFocus />
          </div>
          {species.status === 'error' && (
            <p className="mt-4 text-sm text-red-400">
              Couldn't load species ({species.message}). Check that the Spring backend is running on port 8080.
            </p>
          )}
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-4 py-12">
        <h2 className="mb-6 flex items-center gap-3 text-2xl font-bold">
          <span className="h-7 w-1 rounded bg-brand" aria-hidden="true" />
          Popular in GO Battle League
        </h2>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {POPULAR_IDS.map((id) => {
            const option = species.status === 'ready' ? species.byId.get(id) : undefined
            return (
              <Link
                key={id}
                to={`/species/${id}`}
                className="group overflow-hidden rounded-lg bg-zinc-900 ring-1 ring-zinc-800 transition hover:ring-brand"
              >
                <div className="aspect-square bg-zinc-800/50 p-4">
                  <PokemonArt id={id} alt={option ? displayName(option) : ''} className="h-full w-full transition group-hover:scale-105" />
                </div>
                <div className="p-3">
                  <div className="text-xs text-zinc-500">{formatDex(id)}</div>
                  <div className="truncate font-semibold">{option ? displayName(option) : '…'}</div>
                </div>
              </Link>
            )
          })}
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-4 pb-16">
        <div className="grid gap-4 sm:grid-cols-3">
          {EXPLAINERS.map((item) => (
            <div key={item.title} className="rounded-lg bg-zinc-900 p-5 ring-1 ring-zinc-800">
              <h3 className="font-bold text-brand">{item.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-zinc-400">{item.body}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}

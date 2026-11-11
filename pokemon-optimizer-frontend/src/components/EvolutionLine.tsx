import { Fragment, useState } from 'react'
import { useNavigate } from 'react-router'
import { getEvolutions } from '../api'
import type { CaughtPokemon, CaughtPokemonRequest, EvolutionProjection, FormProjection, LeagueProjection } from '../types'
import { formatLevel } from '../lib/species'
import type { Simulation } from '../lib/simulate'
import { PokemonArt } from './PokemonArt'

const LEAGUE_NAMES = { GREAT: 'Great League', ULTRA: 'Ultra League', MASTER: 'Master League' } as const

type Load =
  | { status: 'closed' }
  | { status: 'loading' }
  | { status: 'ready'; projection: EvolutionProjection }
  | { status: 'error'; message: string }

/**
 * A Collection card's evolution line (#140): a toggle per form this Pokémon can evolve into, each with its Great,
 * Ultra and Master League build, and a button that sends that form to the Team Builder in place of this one.
 * The projection is asked for only when the line is opened.
 */
export function EvolutionLine({ pokemon, name }: { pokemon: CaughtPokemon; name: string }) {
  const [load, setLoad] = useState<Load>({ status: 'closed' })
  const [selected, setSelected] = useState(0)
  const navigate = useNavigate()
  const open = load.status !== 'closed'

  async function toggle() {
    if (open) {
      setLoad({ status: 'closed' })
      return
    }
    setLoad({ status: 'loading' })
    try {
      const { speciesId, atkIv, defIv, staIv, level, shadow } = pokemon
      setLoad({ status: 'ready', projection: await getEvolutions({ speciesId, atkIv, defIv, staIv, level, shadow }) })
      setSelected(0)
    } catch (error: unknown) {
      setLoad({ status: 'error', message: error instanceof Error ? error.message : String(error) })
    }
  }

  function simulate(form: FormProjection, league: LeagueProjection) {
    const { speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow } = pokemon
    const from: CaughtPokemonRequest = { speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow }
    const as: CaughtPokemonRequest = {
      ...from,
      speciesId: form.speciesId,
      fastMove: form.moves.optimalFastMove ?? '',
      chargedMoves: form.moves.optimalChargedMoves,
    }
    const simulation: Simulation = { league: league.league, from, as, fromName: name, asName: form.name }
    navigate('/team-builder', { state: { simulate: simulation } })
  }

  const forms = load.status === 'ready' ? load.projection.forms : []
  const form = forms[selected]

  return (
    <div className="mt-3 border-t border-line pt-2">
      <button
        type="button"
        onClick={toggle}
        aria-expanded={open}
        className="flex w-full items-center justify-between text-xs font-semibold text-ink-soft hover:text-ink"
      >
        Evolutions
        <span aria-hidden className={`transition ${open ? 'rotate-90' : ''}`}>
          ›
        </span>
      </button>

      {load.status === 'loading' && <div className="mt-2 h-24 animate-pulse rounded-lg bg-raised" />}
      {load.status === 'error' && <p className="mt-2 text-xs text-red-400">Couldn't load its evolutions ({load.message})</p>}
      {load.status === 'ready' && forms.length === 0 && <p className="mt-2 text-xs text-subtle">Nothing to evolve into.</p>}

      {load.status === 'ready' && form && (
        <div className="mt-2">
          <div className="flex flex-wrap items-center gap-1.5 text-xs">
            <span className="rounded-md bg-raised px-2 py-1 text-subtle ring-1 ring-line" title="The one you have">
              {name}
            </span>
            <div role="tablist" aria-label="Evolution stages" className="contents">
              {forms.map((f, i) => (
                <Fragment key={f.speciesId}>
                  <span aria-hidden className="text-subtle">
                    {i > 0 && forms[i - 1].stage === f.stage ? '/' : '→'}
                  </span>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={i === selected}
                    onClick={() => setSelected(i)}
                    className={`rounded-md px-2 py-1 font-semibold ring-1 transition ${
                      i === selected ? 'bg-brand text-on-brand ring-brand' : 'bg-raised text-ink-soft ring-line-strong hover:text-ink'
                    }`}
                  >
                    {f.name}
                  </button>
                </Fragment>
              ))}
            </div>
          </div>

          <div role="tabpanel" aria-label={form.name} className="mt-2 flex gap-3">
            <div className="h-14 w-14 shrink-0 rounded-lg bg-canvas/60 p-1">
              <PokemonArt dex={form.dex} alt="" className="h-full w-full" />
            </div>
            <ul className="min-w-0 flex-1 space-y-1">
              {form.leagues.map((l) => (
                <LeagueRow key={l.league} league={l} canSimulate={form.moves.optimalFastMove !== null} onSimulate={() => simulate(form, l)} formName={form.name} />
              ))}
            </ul>
          </div>
          {form.moves.warnings.map((w) => (
            <p key={w.moveId} className="mt-1.5 text-xs leading-relaxed text-amber-300">
              {w.message}
            </p>
          ))}
          {form.moves.optimalFastMove === null && (
            <p className="mt-1.5 text-xs text-subtle">No suggested moveset for {form.name}, so it can't be simulated yet.</p>
          )}
        </div>
      )}
    </div>
  )
}

function LeagueRow({
  league,
  formName,
  canSimulate,
  onSimulate,
}: {
  league: LeagueProjection
  formName: string
  canSimulate: boolean
  onSimulate: () => void
}) {
  const leagueName = LEAGUE_NAMES[league.league]
  return (
    <li aria-label={leagueName} className="flex items-center justify-between gap-2 rounded-md bg-raised/60 px-2 py-1 text-xs ring-1 ring-line">
      <span className="min-w-0">
        <span className="font-semibold text-ink">{leagueName.replace(' League', '')}</span>{' '}
        {league.eligible && league.cp !== null ? (
          <span className="tabular-nums text-ink-soft">
            {league.cp.toLocaleString('en-US')} CP · Lv {formatLevel(league.level ?? 1)}
            {league.percentOfBest !== null && <> · {league.percentOfBest.toFixed(1)}%</>}
            {league.needsXlCandy && <span className="ml-1 rounded bg-sky-500/20 px-1 text-[10px] font-bold text-sky-300">XL</span>}
            {league.bestBuddyCp !== null && <span className="block text-subtle">Best Buddy {league.bestBuddyCp.toLocaleString('en-US')} CP</span>}
          </span>
        ) : (
          <span className="text-subtle" title="Already over the CP cap at its level, and levels can't go down">
            Too strong
          </span>
        )}
      </span>
      {league.eligible && canSimulate && (
        <button
          type="button"
          onClick={onSimulate}
          aria-label={`Simulate ${formName} in ${leagueName}`}
          className="btn btn-secondary btn-sm shrink-0 px-2 text-[11px]"
        >
          Simulate
        </button>
      )}
    </li>
  )
}

import { useEffect, useMemo, useState } from 'react'
import { useParams, useSearchParams } from 'react-router'
import { getRank, getSpeciesDetail } from '../api'
import type { RankedIvResult, SpeciesDetail, SpeciesOption } from '../types'
import { useSpecies } from '../species/species-context'
import { PokemonArt } from '../components/PokemonArt'
import { IvTriple } from '../components/IvTriple'
import { AddToCollection } from '../components/AddToCollection'
import { formatDex, formatLevel } from '../lib/species'
import { useMoves } from '../moves/moves-context'
import { DEFAULT_LEAGUE, LEAGUES, leagueFromKey, type League } from '../lib/leagues'
import { NotFoundPage } from './NotFoundPage'

const PAGE_SIZE = 50
const IV_VALUES = Array.from({ length: 16 }, (_, i) => i)

export function SpeciesPage() {
  const { id = '' } = useParams()
  const species = useSpecies()

  if (species.status === 'ready' && !species.byId.has(id)) {
    return <NotFoundPage message={`No Pokémon with ID “${id}”.`} />
  }
  const option = species.status === 'ready' ? species.byId.get(id) : undefined

  // key resets the page's local state (IV checker, rows shown) when switching species
  return <SpeciesView key={id} id={id} option={option} />
}

type RankingResult = { key: string; rows: RankedIvResult[] } | { key: string; error: string }

function SpeciesView({ id, option }: { id: string; option?: SpeciesOption }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const league = leagueFromKey(searchParams.get('league'))
  const bestBuddy = searchParams.get('bb') === '1'

  const [result, setResult] = useState<RankingResult | null>(null)
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE)
  const [myIvs, setMyIvs] = useState({ atk: 0, def: 15, sta: 15 })
  const [detail, setDetail] = useState<SpeciesDetail | 'error' | null>(null)

  // SpeciesView is keyed by id, so this runs once per species
  useEffect(() => {
    let cancelled = false
    getSpeciesDetail(id)
      .then((d) => {
        if (!cancelled) setDetail(d)
      })
      .catch(() => {
        if (!cancelled) setDetail('error')
      })
    return () => {
      cancelled = true
    }
  }, [id])

  const requestKey = `${id}|${league.key}|${bestBuddy}`

  useEffect(() => {
    let cancelled = false
    getRank({ speciesId: id, hasCap: league.hasCap, cap: league.cap, hasBestBuddy: bestBuddy })
      .then((rows) => {
        if (cancelled) return
        const sorted = [...rows].sort((a, b) => a.statProductRank - b.statProductRank)
        setResult({ key: requestKey, rows: sorted })
      })
      .catch((error: unknown) => {
        if (cancelled) return
        setResult({ key: requestKey, error: error instanceof Error ? error.message : String(error) })
      })
    // a newer request replaces this one, so ignore its late answer
    return () => {
      cancelled = true
    }
  }, [id, league, bestBuddy, requestKey])

  const current = result?.key === requestKey ? result : null
  const rows = current && 'rows' in current ? current.rows : null

  const byIvs = useMemo(() => {
    const map = new Map<string, RankedIvResult>()
    rows?.forEach((r) => map.set(ivKey(r.ivResult.atkIV, r.ivResult.defIV, r.ivResult.staIV), r))
    return map
  }, [rows])

  function updateParams(next: { league?: League; bestBuddy?: boolean }) {
    const params = new URLSearchParams(searchParams)
    const nextLeague = next.league ?? league
    const nextBuddy = next.bestBuddy ?? bestBuddy
    if (nextLeague === DEFAULT_LEAGUE) params.delete('league')
    else params.set('league', nextLeague.key)
    if (nextBuddy) params.set('bb', '1')
    else params.delete('bb')
    setSearchParams(params, { replace: true })
    setVisibleCount(PAGE_SIZE)
  }

  const best = rows?.[0]
  const mine = byIvs.get(ivKey(myIvs.atk, myIvs.def, myIvs.sta))
  const title = option ? option.name : 'Loading…'

  return (
    <div>
      {/* Hero */}
      <section className="border-b border-line bg-gradient-to-b from-surface to-canvas">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-8 sm:flex-row sm:items-center sm:py-10">
          <div className="mx-auto aspect-square w-48 shrink-0 rounded-xl bg-raised/60 p-4 ring-1 ring-line-strong sm:mx-0 sm:w-56">
            <PokemonArt dex={option?.dex} alt={option?.name ?? ''} className="h-full w-full" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="text-sm text-subtle">{option ? formatDex(option.dex) : '\u00a0'}</div>
            <h1 className="mt-1 flex flex-wrap items-baseline gap-3 text-4xl font-black tracking-tight sm:text-5xl">
              {title}
            </h1>

            <BaseStats detail={detail} />

            <div className="mt-6 flex flex-wrap items-center gap-4">
              <div role="tablist" aria-label="League" className="inline-flex rounded-lg bg-surface p-1 ring-1 ring-line">
                {LEAGUES.map((l) => (
                  <button
                    key={l.key}
                    role="tab"
                    aria-selected={l.key === league.key}
                    onClick={() => updateParams({ league: l })}
                    className={`rounded-md px-3 py-2 text-left text-sm transition sm:px-4 ${
                      l.key === league.key ? 'bg-brand text-on-brand' : 'text-ink-soft hover:bg-raised'
                    }`}
                  >
                    <div className="font-bold">{l.label.replace(' League', '')}</div>
                    <div className={`text-xs ${l.key === league.key ? 'text-on-brand/70' : 'text-subtle'}`}>{l.cpLabel}</div>
                  </button>
                ))}
              </div>

              <label className="flex cursor-pointer items-center gap-3 text-sm text-ink-soft">
                <input
                  type="checkbox"
                  checked={bestBuddy}
                  onChange={(e) => updateParams({ bestBuddy: e.target.checked })}
                  className="peer sr-only"
                />
                <span className="relative h-6 w-11 rounded-full bg-raised-strong transition peer-checked:bg-brand peer-focus-visible:ring-2 peer-focus-visible:ring-white after:absolute after:left-0.5 after:top-0.5 after:h-5 after:w-5 after:rounded-full after:bg-white after:transition peer-checked:after:translate-x-5" />
                Best Buddy <span className="text-subtle">(+1 level)</span>
              </label>
            </div>
          </div>
        </div>
      </section>

      <Moves detail={detail} />

      <div className="mx-auto max-w-6xl px-4 py-8">
        {current && 'error' in current ? (
          <div role="alert" className="rounded-lg border border-red-900 bg-red-950/50 p-5 text-red-200">
            <p className="font-semibold">Couldn't load rankings</p>
            <p className="mt-1 text-sm text-red-300/80">{current.error}. Check that the Spring backend is running on port 8080.</p>
          </div>
        ) : (
          <>
            {/* Summary cards */}
            <div className="grid gap-4 lg:grid-cols-2">
              <div className="rounded-lg bg-surface p-5 ring-1 ring-line">
                <h2 className="text-sm font-semibold uppercase tracking-wider text-muted">Rank 1 · {league.label}</h2>
                {best ? (
                  <div className="mt-3 flex flex-wrap items-end gap-x-8 gap-y-3">
                    <IvTriple atk={best.ivResult.atkIV} def={best.ivResult.defIV} sta={best.ivResult.staIV} large />
                    <Stat label="Level" value={formatLevel(best.ivResult.level)} />
                    <Stat label="Stat Product" value={formatInt(best.ivResult.statProduct)} />
                  </div>
                ) : (
                  <Skeleton className="mt-3 h-12 w-3/4" />
                )}
              </div>

              <div className="rounded-lg bg-surface p-5 ring-1 ring-line">
                <h2 className="text-sm font-semibold uppercase tracking-wider text-muted">Check your Pokémon</h2>
                <div className="mt-3 flex flex-wrap items-end gap-3">
                  {(['atk', 'def', 'sta'] as const).map((stat) => (
                    <label key={stat} className="text-xs uppercase text-subtle">
                      {stat}
                      <select
                        value={myIvs[stat]}
                        onChange={(e) => setMyIvs({ ...myIvs, [stat]: Number(e.target.value) })}
                        className="mt-1 block w-16 rounded-md bg-raised px-2 py-1.5 text-base text-ink ring-1 ring-line-strong focus:outline-none focus:ring-brand"
                      >
                        {IV_VALUES.map((v) => (
                          <option key={v} value={v}>
                            {v}
                          </option>
                        ))}
                      </select>
                    </label>
                  ))}
                  {mine && best ? (
                    <div className="ml-auto flex gap-6">
                      <Stat label="Rank" value={`#${mine.statProductRank}`} highlight={mine.statProductRank <= 100} />
                      <Stat label="% of #1" value={`${percentOf(mine, best)}%`} />
                      <Stat label="Mirror" value={`#${mine.mirrorRank}`} />
                    </div>
                  ) : (
                    <Skeleton className="ml-auto h-10 w-48" />
                  )}
                </div>
                <AddToCollection speciesId={id} ivs={myIvs} />
              </div>
            </div>

            {/* Rankings table */}
            <h2 className="mb-4 mt-10 flex items-center gap-3 text-2xl font-bold">
              <span className="h-7 w-1 rounded bg-brand" aria-hidden="true" />
              IV Rankings
              <span className="text-base font-normal text-subtle">4,096 spreads</span>
            </h2>

            <div className="overflow-x-auto rounded-lg ring-1 ring-line">
              <table className="w-full min-w-[640px] text-sm">
                <thead className="bg-surface text-left text-xs uppercase tracking-wider text-muted">
                  <tr>
                    <th className="px-4 py-3">Rank</th>
                    <th className="px-4 py-3">IVs (Atk / Def / Sta)</th>
                    <th className="px-4 py-3 text-right">Level</th>
                    <th className="px-4 py-3 text-right">Stat Product</th>
                    <th className="px-4 py-3 text-right">% of #1</th>
                    <th className="px-4 py-3 text-right">Mirror Rank</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line/70">
                  {rows && best
                    ? rows.slice(0, visibleCount).map((r) => {
                        const isMine = r === mine
                        return (
                          <tr key={r.statProductRank} className={isMine ? 'bg-brand/15' : 'hover:bg-surface/70'}>
                            <td className="px-4 py-2.5 font-semibold tabular-nums">
                              <span className={r.statProductRank <= 10 ? 'text-brand' : ''}>#{r.statProductRank}</span>
                              {isMine && <span className="ml-2 rounded bg-brand px-1.5 py-0.5 text-[10px] font-bold uppercase text-on-brand">Yours</span>}
                            </td>
                            <td className="px-4 py-2.5">
                              <IvTriple atk={r.ivResult.atkIV} def={r.ivResult.defIV} sta={r.ivResult.staIV} />
                            </td>
                            <td className="px-4 py-2.5 text-right tabular-nums">{formatLevel(r.ivResult.level)}</td>
                            <td className="px-4 py-2.5 text-right tabular-nums">{formatInt(r.ivResult.statProduct)}</td>
                            <td className="px-4 py-2.5 text-right tabular-nums">{percentOf(r, best)}%</td>
                            <td className="px-4 py-2.5 text-right tabular-nums text-muted">#{r.mirrorRank}</td>
                          </tr>
                        )
                      })
                    : Array.from({ length: 10 }, (_, i) => (
                        <tr key={i}>
                          <td colSpan={6} className="px-4 py-3">
                            <Skeleton className="h-5 w-full" />
                          </td>
                        </tr>
                      ))}
                </tbody>
              </table>
            </div>

            {rows && visibleCount < rows.length && (
              <div className="mt-6 text-center">
                <button
                  onClick={() => setVisibleCount((n) => n + PAGE_SIZE * 2)}
                  className="rounded-md bg-raised px-5 py-2.5 text-sm font-semibold text-ink transition hover:bg-raised-strong"
                >
                  Show more <span className="text-muted">({visibleCount} of {rows.length})</span>
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}

// bars are scaled against this; only a handful of species exceed it
const STAT_BAR_MAX = 350

function BaseStats({ detail }: { detail: SpeciesDetail | 'error' | null }) {
  if (detail === 'error') {
    return <p className="mt-4 text-sm text-subtle">Base stats unavailable.</p>
  }

  const stats = [
    { label: 'Attack', value: detail?.baseAtk },
    { label: 'Defense', value: detail?.baseDef },
    { label: 'Stamina', value: detail?.baseSta },
  ]

  return (
    <dl className="mt-4 grid max-w-md gap-2">
      {stats.map(({ label, value }) => (
        <div key={label} className="grid grid-cols-[4.5rem_2.5rem_1fr] items-center gap-3 text-sm">
          <dt className="text-muted">{label}</dt>
          <dd className="text-right font-semibold tabular-nums">{value ?? '–'}</dd>
          <div className="h-2 rounded-full bg-raised" aria-hidden="true">
            {value !== undefined && (
              <div
                className="h-2 rounded-full bg-brand"
                style={{ width: `${Math.min(value / STAT_BAR_MAX, 1) * 100}%` }}
              />
            )}
          </div>
        </div>
      ))}
    </dl>
  )
}

function Moves({ detail }: { detail: SpeciesDetail | 'error' | null }) {
  const moveList = useMoves()

  // BaseStats already reports the failed request
  if (detail === 'error') return null

  const groups = [
    { label: 'Fast Moves', moves: detail?.learnset.fastMoves },
    { label: 'Charged Moves', moves: detail?.learnset.chargedMoves },
  ]
  const eliteMoves = detail?.learnset.eliteMoves ?? []
  // fall back to the raw ID if the move list failed to load or lacks this move
  const nameOf = (id: string) => (moveList.status === 'ready' ? moveList.byId.get(id)?.name : undefined) ?? id

  return (
    <section className="border-b border-line">
      <div className="mx-auto max-w-6xl px-4 py-6">
        <div className="grid gap-6 sm:grid-cols-2">
          {groups.map(({ label, moves }) => (
            <div key={label}>
              <h2 className="text-sm font-semibold uppercase tracking-wider text-muted">{label}</h2>
              <ul className="mt-3 flex flex-wrap gap-2">
                {moves && moveList.status !== 'loading'
                  ? moves.map((move) => {
                      const elite = eliteMoves.includes(move)
                      return (
                        <li
                          key={move}
                          className={`rounded-md px-3 py-1.5 text-sm ring-1 ${
                            elite ? 'bg-brand/10 text-brand ring-brand/60' : 'bg-surface text-ink ring-line'
                          }`}
                        >
                          {nameOf(move)}
                          {elite && <span className="ml-1.5" aria-label="Elite Move" title="Elite Move">★</span>}
                        </li>
                      )
                    })
                  : Array.from({ length: 3 }, (_, i) => (
                      <li key={i}>
                        <Skeleton className="h-8 w-24" />
                      </li>
                    ))}
              </ul>
            </div>
          ))}
        </div>
        {eliteMoves.length > 0 && (
          <p className="mt-4 text-xs text-subtle">
            <span className="text-brand">★</span> Elite Move: can't be learned normally; needs an Elite TM or came from a past event.
          </p>
        )}
      </div>
    </section>
  )
}

function Stat({ label, value, highlight = false }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div>
      <div className="text-xs uppercase text-subtle">{label}</div>
      <div className={`text-2xl font-bold tabular-nums ${highlight ? 'text-brand' : ''}`}>{value}</div>
    </div>
  )
}

function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded bg-raised ${className}`} />
}

function ivKey(atk: number, def: number, sta: number) {
  return `${atk}-${def}-${sta}`
}

function formatInt(n: number) {
  return Math.round(n).toLocaleString()
}

function percentOf(row: RankedIvResult, best: RankedIvResult) {
  return ((row.ivResult.statProduct / best.ivResult.statProduct) * 100).toFixed(2)
}

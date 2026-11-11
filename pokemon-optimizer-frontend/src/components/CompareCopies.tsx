import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { compareCopies } from '../api'
import { useCollection } from '../collection/collection-context'
import { useMetaName } from '../species/meta-name'
import { copyLabel, MAX_COMPARED, pickCopies, shieldsLabel, winnersLabel, winRate, winRateEdge } from '../lib/compare'
import { formatLevel } from '../lib/species'
import type { LeagueKey } from '../lib/leagues'
import type { CaughtPokemon, IvComparisonResult, TeamBuilderLeague } from '../types'

const BATTLE_LEAGUE: Partial<Record<LeagueKey, TeamBuilderLeague>> = { great: 'GREAT', ultra: 'ULTRA', master: 'MASTER' }
const FLIPS_SHOWN = 10

type Answer = { request: string; result: IvComparisonResult } | { request: string; error: string }

/**
 * "Which one to build": the player's copies of this species, each powered to its best level under the cap,
 * battled against the League's meta. Ranked by battles won, with the matchups where they part ways.
 */
export function CompareCopies({
  speciesId,
  leagueKey,
  leagueLabel,
  rankOf,
}: {
  speciesId: string
  leagueKey: LeagueKey
  leagueLabel: string
  /** stat product rank for the League; null while the ranking is still loading */
  rankOf: ((p: CaughtPokemon) => number | undefined) | null
}) {
  const collection = useCollection()
  const metaName = useMetaName()
  const [answer, setAnswer] = useState<Answer | null>(null)
  const [showAll, setShowAll] = useState(false)
  const section = useRef<HTMLElement>(null)
  const { hash } = useLocation()

  // linked to from the nav, the Team Builder or a match review (#118): bring the comparison into view
  useEffect(() => {
    if (hash === '#compare') section.current?.scrollIntoView?.({ block: 'start' })
  }, [hash])

  const copies = useMemo(
    () => (collection.status === 'ready' ? collection.list.filter((p) => p.speciesId === speciesId) : []),
    [collection, speciesId],
  )
  const picked = useMemo(() => (rankOf ? pickCopies(copies, rankOf) : []), [copies, rankOf])
  const league = BATTLE_LEAGUE[leagueKey]
  const request = league && picked.length >= 2 ? `${league}|${picked.map((p) => p.id).join(',')}` : null

  useEffect(() => {
    if (!league || !request) return
    const controller = new AbortController()
    const pokemon = picked.map(({ speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow }) => ({
      speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow,
    }))
    compareCopies(league, pokemon, controller.signal)
      .then((result) => setAnswer({ request, result }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setAnswer({ request, error: error instanceof Error ? error.message : String(error) })
      })
    return () => controller.abort()
  }, [league, request, picked])

  if (collection.status !== 'ready') return null
  const usable = copies.filter((p) => p.fastMove && p.chargedMoves.length > 0).length

  // the same IVs twice get a number so every line still says which copy it means
  const seen = new Map<string, number>()
  const labels = picked.map((p) => {
    const label = copyLabel(p)
    const n = (seen.get(label) ?? 0) + 1
    seen.set(label, n)
    return n === 1 ? label : `${label} (#${n})`
  })

  const current = answer?.request === request ? answer : null

  return (
    <section ref={section} id="compare" className="mt-10 scroll-mt-24">
      <h2 className="mb-1 flex items-center gap-3 font-display text-2xl">
        <span className="h-7 w-1.5 rounded bg-gradient-to-b from-creamer to-brand" aria-hidden="true" />
        Which One to Build
      </h2>
      <p className="mb-4 text-sm text-subtle">
        Your copies, each powered to its best level for {leagueLabel}, battled against the meta at 0, 1 and 2 shields.
      </p>

      {!league ? (
        <p className="card p-5 text-sm text-ink-soft">Comparing copies works for Great, Ultra and Master League.</p>
      ) : usable < 2 ? (
        <div className="card p-5 text-sm text-ink-soft">
          {usable === 0 ? 'No copies of this one in your Collection yet. ' : 'One copy so far. '}
          Pick each copy's IVs under <span className="text-ink">Check your Pokémon</span> above and add it, or{' '}
          <Link to="/collection?view=exact" className="text-brand underline">
            add them in your Collection
          </Link>
          . With two or more, this shows which one wins more.
        </div>
      ) : !current ? (
        <div className="card h-40 animate-pulse" aria-label="Comparing your copies" />
      ) : 'error' in current ? (
        <p role="alert" className="card p-5 text-sm text-ink-soft">
          Couldn't compare them: {current.error}
        </p>
      ) : (
        <Comparison result={current.result} labels={labels} metaName={metaName} showAll={showAll} onShowAll={() => setShowAll(true)}>
          {usable > MAX_COMPARED && (
            <p className="mt-3 text-xs text-subtle">
              Comparing your {MAX_COMPARED} best by stat product out of {usable}.
            </p>
          )}
        </Comparison>
      )}
    </section>
  )
}

function Comparison({
  result,
  labels,
  metaName,
  showAll,
  onShowAll,
  children,
}: {
  result: IvComparisonResult
  labels: string[]
  metaName: (id: string) => string
  showAll: boolean
  onShowAll: () => void
  children: React.ReactNode
}) {
  const playing = result.pokemon.filter((p) => p.problem === null)
  const [first, second] = playing
  const tied = first && second && first.wins === second.wins
  const flips = showAll ? result.flips : result.flips.slice(0, FLIPS_SHOWN)

  return (
    <div className="card p-5 sm:p-6">
      {first && second && (
        <>
          <HeadToHead first={first} second={second} labels={labels} tied={tied} />
          <p className="mb-4 text-ink">
            {tied ? (
              <>Your top copies win the same battles. Power up whichever costs less dust.</>
            ) : (
              <>
                <span className="font-bold text-brand">{labels[first.index]}</span> wins {first.wins - second.wins} more{' '}
                {first.wins - second.wins === 1 ? 'battle' : 'battles'} than {labels[second.index]}. Build that one.
              </>
            )}
          </p>
        </>
      )}

      <ol className="grid gap-2">
        {result.pokemon.map((p, i) => (
          <li
            key={p.index}
            className={`grid grid-cols-[1fr_auto] items-center gap-x-4 gap-y-1 rounded-xl px-4 py-3 ring-1 sm:grid-cols-[12rem_1fr_auto] ${
              i === 0 && p.problem === null && !tied ? 'bg-brand/10 ring-brand/60' : 'bg-raised/50 ring-line'
            }`}
          >
            <div className="font-semibold tabular-nums">
              {labels[p.index]}
              {i === 0 && p.problem === null && !tied && (
                <span className="ml-2 rounded bg-brand px-1.5 py-0.5 align-middle text-[10px] font-bold uppercase text-on-brand">
                  Build this
                </span>
              )}
            </div>
            {p.problem ? (
              <p className="col-span-2 text-sm text-subtle sm:col-span-1">{p.problem}, so it can't play here.</p>
            ) : (
              <>
                <div className="order-last col-span-2 sm:order-none sm:col-span-1">
                  <div className="h-2 overflow-hidden rounded-full bg-canvas/70" aria-hidden="true">
                    <div className="h-full rounded-full bg-brand" style={{ width: `${(p.wins / p.battles) * 100}%` }} />
                  </div>
                </div>
                <div className="text-right text-sm text-ink-soft">
                  <span className="font-semibold tabular-nums text-ink">{p.wins}</span> of {p.battles} won
                  <span className="block text-xs text-subtle tabular-nums">
                    Lv {formatLevel(p.level)} · {p.cp.toLocaleString()} CP
                  </span>
                </div>
              </>
            )}
          </li>
        ))}
      </ol>
      {children}

      {result.flips.length > 0 && (
        <div className="mt-6">
          <h3 className="eyebrow">Where they differ</h3>
          <ul className="mt-2 divide-y divide-line/60 text-sm">
            {flips.map((flip) => (
              <li key={`${flip.opponent}-${flip.shields}`} className="flex flex-wrap gap-x-2 py-2">
                <span className="font-semibold text-ink">vs {metaName(flip.opponent)}</span>
                <span className="text-subtle">{shieldsLabel(flip.shields)}:</span>
                <span className="text-ink-soft">{winnersLabel(flip.winners.map((w) => labels[w]))}</span>
              </li>
            ))}
          </ul>
          {!showAll && result.flips.length > FLIPS_SHOWN && (
            <button type="button" onClick={onShowAll} className="btn btn-ghost btn-sm mt-2">
              Show all {result.flips.length}
            </button>
          )}
        </div>
      )}
      <p className="mt-4 text-xs text-subtle">
        Against {result.metaSize} meta Pokémon (PvPoke, {result.metaDate}). Each copy keeps the moves you saved it with.
      </p>
    </div>
  )
}

/**
 * The top two copies side by side (#118): each one's meta win rate, and the better one's bold "Build This One" badge
 * with how many percentage points more of the meta it beats.
 */
function HeadToHead({
  first,
  second,
  labels,
  tied,
}: {
  first: IvComparisonResult['pokemon'][number]
  second: IvComparisonResult['pokemon'][number]
  labels: string[]
  tied: boolean
}) {
  const edge = winRateEdge(first, second)
  return (
    <div
      role="group"
      aria-label={tied ? 'Your top two copies' : 'Build This One'}
      className="relative mb-5 grid grid-cols-2 gap-3 sm:gap-4"
    >
      {[first, second].map((p, i) => {
        const winner = i === 0 && !tied
        return (
          <article
            key={p.index}
            aria-label={labels[p.index]}
            className={`animate-rise flex flex-col items-center rounded-2xl px-3 py-4 text-center ring-1 sm:px-5 ${
              winner ? 'bg-brand/12 ring-2 ring-brand/70' : 'bg-raised/50 ring-line'
            }`}
            style={{ animationDelay: `${i * 90}ms` }}
          >
            <p className="text-base font-bold tabular-nums text-ink sm:text-lg">{labels[p.index]}</p>
            <p className="text-xs tabular-nums text-subtle">
              Lv {formatLevel(p.level)} · {p.cp.toLocaleString()} CP
            </p>
            <p className={`mt-2 font-display text-3xl sm:text-4xl ${winner ? 'text-brand' : 'text-ink-soft'}`}>
              {winRate(p).toFixed(1)}%
            </p>
            <p className="text-xs text-subtle">meta win rate</p>
            {winner && (
              <span className="mt-3 rounded-full bg-brand px-3 py-1 text-xs font-black tracking-wide text-on-brand uppercase shadow">
                Build This One: +{edge.toFixed(1)}% Meta Win Rate
              </span>
            )}
          </article>
        )
      })}
      <span
        aria-hidden="true"
        className="absolute top-1/2 left-1/2 grid h-9 w-9 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-canvas text-xs font-black text-subtle ring-1 ring-line"
      >
        VS
      </span>
    </div>
  )
}

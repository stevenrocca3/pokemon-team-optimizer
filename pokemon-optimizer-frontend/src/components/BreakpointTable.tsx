import { useEffect, useState } from 'react'
import { getBreakpoints } from '../api'
import { useMoves } from '../moves/moves-context'
import { useMetaName } from '../species/meta-name'
import { formatLevel } from '../lib/species'
import { formatStat } from '../lib/stats'
import type { LeagueKey } from '../lib/leagues'
import type { BreakpointReport, SpeciesDetail, TeamBuilderLeague } from '../types'

const BATTLE_LEAGUE: Partial<Record<LeagueKey, TeamBuilderLeague>> = { great: 'GREAT', ultra: 'ULTRA', master: 'MASTER' }

type Answer = { request: string; report: BreakpointReport } | { request: string; error: string }

/**
 * Breakpoints and bulkpoints for the IVs being checked: against each meta Pokémon, the fast move damage both
 * ways, and the Attack or Defense that would change it by one. Only thresholds some IV spread reaches under the
 * cap are shown, since those are what a different copy could buy.
 */
export function BreakpointTable({
  detail,
  ivs,
  leagueKey,
  leagueLabel,
}: {
  detail: SpeciesDetail | null
  ivs: { atk: number; def: number; sta: number }
  leagueKey: LeagueKey
  leagueLabel: string
}) {
  const moves = useMoves()
  const metaName = useMetaName()
  const [fastChoice, setFastChoice] = useState('')
  const [shadowChoice, setShadowChoice] = useState(false)
  const [showAll, setShowAll] = useState(false)
  const [answer, setAnswer] = useState<Answer | null>(null)

  const league = BATTLE_LEAGUE[leagueKey]
  const fastMove = fastChoice || detail?.suggestedMoves?.fastMove || detail?.learnset.fastMoves[0] || ''
  const chargedMove = detail?.suggestedMoves?.chargedMoves[0] || detail?.learnset.chargedMoves[0] || ''
  const shadow = shadowChoice && detail?.shadowEligible === true
  const request =
    detail && league && fastMove && chargedMove
      ? `${detail.id}|${league}|${ivs.atk}/${ivs.def}/${ivs.sta}|${fastMove}|${shadow}`
      : null

  useEffect(() => {
    if (!detail || !league || !request) return
    const controller = new AbortController()
    const pokemon = {
      speciesId: detail.id, atkIv: ivs.atk, defIv: ivs.def, staIv: ivs.sta, level: null,
      fastMove, chargedMoves: [chargedMove], shadow,
    }
    getBreakpoints(league, pokemon, controller.signal)
      .then((report) => setAnswer({ request, report }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setAnswer({ request, error: error instanceof Error ? error.message : String(error) })
      })
    return () => controller.abort()
  }, [detail, league, request, ivs.atk, ivs.def, ivs.sta, fastMove, chargedMove, shadow])

  const moveName = (id: string) => (moves.status === 'ready' ? moves.byId.get(id)?.name : undefined) ?? id
  const current = answer?.request === request ? answer : null
  const report = current && 'report' in current ? current.report : null
  const rows = report
    ? report.opponents.filter((o) => showAll || o.attackForMore !== null || o.defenseForLess !== null)
    : []

  return (
    <section className="mt-10">
      <h2 className="mb-1 flex items-center gap-3 font-display text-2xl">
        <span className="h-7 w-1.5 rounded bg-gradient-to-b from-creamer to-brand" aria-hidden="true" />
        Breakpoints
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-subtle">
        For the IVs under Check your Pokémon, built for {leagueLabel}: the fast move damage both ways against each meta
        Pokémon, and the Attack or Defense that would change it by one. Only ones some IV spread can reach are listed.
      </p>

      {!league ? (
        <p className="card p-5 text-sm text-ink-soft">Breakpoints work for Great, Ultra and Master League.</p>
      ) : !detail ? (
        <div className="card h-40 animate-pulse" />
      ) : (
        <div className="card p-4 sm:p-5">
          <div className="flex flex-wrap items-end gap-3">
            <label className="text-xs uppercase text-subtle">
              Fast move
              <select value={fastMove} onChange={(e) => setFastChoice(e.target.value)} className="field mt-1 w-48 px-2">
                {detail.learnset.fastMoves.map((id) => (
                  <option key={id} value={id}>
                    {moveName(id)}
                  </option>
                ))}
              </select>
            </label>
            {detail.shadowEligible && (
              <label className="flex h-11 cursor-pointer items-center gap-2 text-sm text-ink-soft">
                <input
                  type="checkbox"
                  checked={shadowChoice}
                  onChange={(e) => setShadowChoice(e.target.checked)}
                  className="h-4 w-4 accent-brand"
                />
                Shadow
              </label>
            )}
            {report && (
              <p className="text-sm text-ink-soft sm:ml-auto">
                Lv {formatLevel(report.level)} · {report.cp.toLocaleString()} CP · Atk{' '}
                <span className="tabular-nums text-ink">{formatStat(report.attack)}</span>
                <span className="text-subtle"> (best {formatStat(report.maxAttack)})</span> · Def{' '}
                <span className="tabular-nums text-ink">{formatStat(report.defense)}</span>
                <span className="text-subtle"> (best {formatStat(report.maxDefense)})</span>
              </p>
            )}
          </div>

          {!current ? (
            <div className="mt-4 h-40 animate-pulse rounded-lg bg-raised/50" aria-label="Working out breakpoints" />
          ) : 'error' in current ? (
            <p role="alert" className="mt-4 text-sm text-ink-soft">
              {current.error}
            </p>
          ) : (
            <>
              <div className="mt-4 overflow-x-auto rounded-lg ring-1 ring-line">
                <table className="w-full min-w-[640px] text-sm">
                  <thead className="bg-surface text-left text-xs uppercase tracking-wider text-muted">
                    <tr>
                      <th className="px-3 py-2.5">Opponent</th>
                      <th className="px-3 py-2.5 text-right">{moveName(report!.fastMove)} does</th>
                      <th className="px-3 py-2.5">Breakpoint</th>
                      <th className="px-3 py-2.5 text-right">They do</th>
                      <th className="px-3 py-2.5">Bulkpoint</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line/70">
                    {rows.map((o) => (
                      <tr key={o.opponent}>
                        <td className="px-3 py-2 font-semibold">{metaName(o.opponent)}</td>
                        <td className="px-3 py-2 text-right tabular-nums">{o.damageDealt}</td>
                        <td className="px-3 py-2 text-ink-soft">
                          {o.attackForMore === null ? (
                            <span className="text-faint">–</span>
                          ) : (
                            <>
                              <span className="font-semibold text-brand">{o.damageDealt + 1}</span> at Atk ≥{' '}
                              <span className="tabular-nums">{formatStat(o.attackForMore, true)}</span>
                            </>
                          )}
                        </td>
                        <td className="px-3 py-2 text-right tabular-nums">
                          {o.damageTaken}
                          <span className="ml-1 text-xs text-subtle">{moveName(o.opponentFastMove)}</span>
                        </td>
                        <td className="px-3 py-2 text-ink-soft">
                          {o.defenseForLess === null ? (
                            <span className="text-faint">–</span>
                          ) : (
                            <>
                              <span className="font-semibold text-brand">{o.damageTaken - 1}</span> at Def ≥{' '}
                              <span className="tabular-nums">{formatStat(o.defenseForLess, true)}</span>
                            </>
                          )}
                        </td>
                      </tr>
                    ))}
                    {rows.length === 0 && (
                      <tr>
                        <td colSpan={5} className="px-3 py-6 text-center text-ink-soft">
                          No other IV spread changes any fast move damage here. These IVs are as good as it gets for that.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
              <button type="button" onClick={() => setShowAll((v) => !v)} className="btn btn-ghost btn-sm mt-2">
                {showAll ? 'Only ones that can change' : `Show all ${report!.opponents.length} meta Pokémon`}
              </button>
            </>
          )}
        </div>
      )}
    </section>
  )
}

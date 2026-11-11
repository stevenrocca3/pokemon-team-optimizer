import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getAnalysisResult, getStoredBattle } from '../api'
import { usePageTitle } from '../lib/page-title'
import type { AnalysisResult } from '../types'
import { MatchTimeline } from '../analysis/MatchTimeline'
import { CritiqueCards, ScoreHero } from '../analysis/MatchReview'
import { TeamNudge } from '../analysis/TeamNudge'
import { BuildNudge } from '../analysis/BuildNudge'
import { loadResult, saveResult } from '../analysis/job-storage'
import { LoginCta } from '../analysis/LoginCta'
import { matchIdFor } from '../analysis/analytics'
import { useCollection } from '../collection/collection-context'

type Load = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; result: AnalysisResult }

/** /match/:jobId: one analysed battle, reviewed. Results don't change, so one this browser has opened loads from it. */
export function MatchPage() {
  usePageTitle('Match Breakdown')
  const collection = useCollection()
  const guest = collection.status === 'ready' && collection.storage === 'browser'
  const { jobId = '' } = useParams()
  const [load, setLoad] = useState<Load>(() => {
    const cached = loadResult(jobId)
    return cached ? { status: 'ready', result: cached } : { status: 'loading' }
  })

  useEffect(() => {
    if (loadResult(jobId)) return
    let live = true
    getAnalysisResult(jobId)
      // forgotten by the analysis service (after a day): a logged-in player's saved history still has it
      .catch(async (e: unknown) => {
        const matchId = matchIdFor(jobId)
        const stored = matchId ? await getStoredBattle(matchId).catch(() => null) : null
        if (!stored) throw e
        return stored
      })
      .then((result) => {
        saveResult(jobId, result)
        if (live) setLoad({ status: 'ready', result })
      })
      .catch((e: unknown) => live && setLoad({ status: 'error', message: e instanceof Error ? e.message : String(e) }))
    return () => {
      live = false
    }
  }, [jobId])

  return (
    <div className="mx-auto max-w-5xl px-4 pt-10 pb-24 sm:pt-14">
      {load.status === 'loading' && <p className="pt-6 text-center text-muted">Loading the breakdown...</p>}
      {load.status === 'error' && (
        <div className="pt-6 text-center">
          <p className="text-ink-soft">{load.message}</p>
          <Link to="/matches" className="mt-4 inline-block text-sm text-brand underline-offset-2 hover:underline">
            Back to past matches
          </Link>
        </div>
      )}
      {load.status === 'ready' && (
        <div className="space-y-6">
          <div className="text-center sm:text-left">
            <p className="eyebrow text-brand">Match breakdown</p>
            <h1 className="mt-1 text-3xl font-black tracking-tight text-ink sm:text-4xl">
              {load.result.turn_log.user_team.join(' / ')} <span className="text-muted">vs</span>{' '}
              {load.result.turn_log.opponent_team.join(' / ')}
            </h1>
          </div>
          <ScoreHero report={load.result.report} />
          <CritiqueCards report={load.result.report} />
          <MatchTimeline result={load.result} />
          <TeamNudge result={load.result} />
          <BuildNudge result={load.result} />
          {guest && <LoginCta compact />}
        </div>
      )}
    </div>
  )
}

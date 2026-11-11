import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getAnalysisResult } from '../api'
import { PageHeader } from '../components/PageHeader'
import { usePageTitle } from '../lib/page-title'
import type { AnalysisResult } from '../types'

type Load = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; result: AnalysisResult }

/** /match/:jobId: one analysed battle. */
export function MatchPage() {
  usePageTitle('Match Breakdown')
  const { jobId = '' } = useParams()
  const [load, setLoad] = useState<Load>({ status: 'loading' })

  useEffect(() => {
    let live = true
    getAnalysisResult(jobId)
      .then((result) => live && setLoad({ status: 'ready', result }))
      .catch(
        (e: unknown) =>
          live &&
          setLoad({
            status: 'error',
            message: e instanceof Error ? e.message : String(e),
          }),
      )
    return () => {
      live = false
    }
  }, [jobId])

  return (
    <div className="mx-auto max-w-5xl px-4 pb-24">
      {load.status === 'loading' && <p className="pt-16 text-center text-muted">Loading the breakdown...</p>}
      {load.status === 'error' && (
        <div className="pt-16 text-center">
          <p className="text-ink-soft">{load.message}</p>
          <Link to="/matches" className="mt-4 inline-block text-sm text-brand underline-offset-2 hover:underline">
            Back to past matches
          </Link>
        </div>
      )}
      {load.status === 'ready' && (
        <PageHeader eyebrow="Match breakdown" title={`${load.result.report.score}/100`}>
          {load.result.report.tier.name}
        </PageHeader>
      )}
    </div>
  )
}

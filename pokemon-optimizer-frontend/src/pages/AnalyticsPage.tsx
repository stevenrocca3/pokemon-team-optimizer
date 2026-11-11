import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getAnalyticsSummary } from '../api'
import { PageHeader } from '../components/PageHeader'
import { usePageTitle } from '../lib/page-title'
import { AnalyticsDashboard } from '../analysis/AnalyticsDashboard'
import { GuestAnalytics } from '../analysis/GuestAnalytics'
import type { AnalyticsSummary } from '../types'

type Load =
  { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; summary: AnalyticsSummary }

/** /analytics: the player's battle analytics; a logged-out visitor gets the login call to action instead (#124). */
export function AnalyticsPage() {
  usePageTitle('Battle Analytics')
  const [load, setLoad] = useState<Load>({ status: 'loading' })

  useEffect(() => {
    let live = true
    getAnalyticsSummary()
      .then((summary) => live && setLoad({ status: 'ready', summary }))
      .catch((e: unknown) => live && setLoad({ status: 'error', message: e instanceof Error ? e.message : String(e) }))
    return () => {
      live = false
    }
  }, [])

  return (
    <div className="mx-auto max-w-5xl px-4 pb-24">
      <PageHeader
        eyebrow="Battle analysis"
        title="Your battle analytics"
        actions={
          <Link to="/analyze" className="btn btn-secondary">
            Upload a battle
          </Link>
        }
      >
        How your play is trending, which of your teams win, and your rank, from every battle you’ve analysed.
      </PageHeader>
      {load.status === 'loading' && <p className="pt-6 text-center text-muted">Loading your analytics...</p>}
      {load.status === 'error' && (
        <p role="alert" className="rounded-lg bg-raised px-4 py-3 text-sm text-ink ring-1 ring-danger">
          {load.message}
        </p>
      )}
      {load.status === 'ready' &&
        (load.summary.access === 'guest' ? <GuestAnalytics /> : <AnalyticsDashboard summary={load.summary} />)}
    </div>
  )
}

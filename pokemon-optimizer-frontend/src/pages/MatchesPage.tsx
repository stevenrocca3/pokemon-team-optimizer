import { Link } from 'react-router'
import { PageHeader } from '../components/PageHeader'
import { usePageTitle } from '../lib/page-title'
import { useAnalysisJob } from '../analysis/analysis-job'

/** /matches: the battles this browser has had analysed, newest first. */
export function MatchesPage() {
  usePageTitle('Past Matches')
  const { pastMatches } = useAnalysisJob()
  return (
    <div className="mx-auto max-w-3xl px-4 pb-24">
      <PageHeader
        eyebrow="Battle analysis"
        title="Past matches"
        actions={
          <>
            <Link to="/analytics" className="btn btn-secondary">
              Your analytics
            </Link>
            <Link to="/analyze" className="btn btn-primary">
              Analyze a battle
            </Link>
          </>
        }
      >
        Every battle you’ve had graded on this device.
      </PageHeader>
      {pastMatches.length === 0 ? (
        <p className="card px-6 py-10 text-center text-ink-soft">
          No matches yet. Upload a recording to get your first score.
        </p>
      ) : (
        <ul className="space-y-3">
          {pastMatches.map((m) => (
            <li key={m.jobId}>
              <Link
                to={`/match/${m.jobId}`}
                className="card flex items-center gap-4 px-5 py-4 transition hover:ring-brand"
              >
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-semibold text-ink">{m.fileName}</span>
                  <span className="block text-sm text-muted">
                    {m.tier ?? 'Scored'} · {new Date(m.finishedAt).toLocaleDateString()}
                  </span>
                </span>
                <span className="font-mono text-lg font-bold text-brand">{m.score}/100</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

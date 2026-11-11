import { useState } from 'react'
import { Link } from 'react-router'
import { useAnalysisJob } from './analysis-job'

/**
 * The floating card in the bottom-left corner (Feedback has the right) that follows the battle analysis on every
 * page, until the player dismisses the finished result. It collapses to its one progress line.
 */
export function AnalysisWidget() {
  const { job, clear } = useAnalysisJob()
  const [open, setOpen] = useState(true)
  if (!job) return null

  const running = job.status !== 'complete' && job.status !== 'failed'
  const headline = running
    ? `Analyzing Gameplay: ${job.progress}% complete...`
    : job.status === 'complete'
      ? `Analysis complete: ${job.score}/100`
      : 'Analysis failed'

  return (
    <section
      aria-label="Battle analysis"
      className="card animate-rise fixed right-4 bottom-4 left-4 z-40 p-4 sm:right-auto sm:left-5 sm:w-96"
    >
      <div className="flex items-center gap-3">
        <span aria-hidden className="relative flex size-2.5 shrink-0">
          {running && <span className="absolute inline-flex size-full animate-ping rounded-full bg-brand opacity-70" />}
          <span
            className={`relative inline-flex size-2.5 rounded-full ${job.status === 'failed' ? 'bg-danger' : 'bg-brand'}`}
          />
        </span>
        <p className="min-w-0 flex-1 text-sm font-semibold text-ink">{headline}</p>
        <button
          type="button"
          onClick={() => setOpen(!open)}
          aria-expanded={open}
          aria-label={open ? 'Hide details' : 'Show details'}
          className="btn btn-ghost btn-sm -my-1 -mr-2 px-2"
        >
          <svg viewBox="0 0 20 20" aria-hidden className={`size-4 transition-transform ${open ? '' : 'rotate-180'}`}>
            <path d="M5 8l5 5 5-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </button>
      </div>

      <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-raised" aria-hidden>
        <div
          className={`h-full rounded-full transition-[width] duration-700 ease-out ${job.status === 'failed' ? 'bg-danger' : 'bg-brand'}`}
          style={{ width: `${job.status === 'failed' ? 100 : job.progress}%` }}
        />
      </div>

      {open && (
        <div className="mt-3 text-sm">
          {running && (
            <>
              <p className="font-mono text-xs text-muted" aria-live="polite">
                {job.message}
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <Link to="/team-builder" className="btn btn-secondary btn-sm">
                  Build Teams While You Wait
                </Link>
                <Link to="/matches" className="btn btn-ghost btn-sm">
                  View Past Matches
                </Link>
              </div>
            </>
          )}
          {job.status === 'complete' && (
            <>
              {job.tier && <p className="text-ink-soft">{job.tier}</p>}
              <div className="mt-3 flex gap-2">
                <Link to={`/match/${job.jobId}`} className="btn btn-primary btn-sm">
                  View breakdown
                </Link>
                <button type="button" onClick={clear} className="btn btn-ghost btn-sm">
                  Dismiss
                </button>
              </div>
            </>
          )}
          {job.status === 'failed' && (
            <>
              <p className="text-ink-soft">{job.error}</p>
              <div className="mt-3 flex gap-2">
                <Link to="/analyze" onClick={clear} className="btn btn-secondary btn-sm">
                  Try another video
                </Link>
                <button type="button" onClick={clear} className="btn btn-ghost btn-sm">
                  Dismiss
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </section>
  )
}

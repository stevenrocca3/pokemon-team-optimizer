import { useEffect } from 'react'
import { Link } from 'react-router'
import { useAnalysisJob } from './analysis-job'

const SHOW_MS = 15_000

/**
 * "Analysis Complete!" on whichever page is open when the job finishes, once per job. It hides itself after 15
 * seconds; the widget keeps the result until it's dismissed.
 */
export function AnalysisToast() {
  const { toast, closeToast } = useAnalysisJob()

  useEffect(() => {
    if (!toast) return
    const timer = setTimeout(closeToast, SHOW_MS)
    return () => clearTimeout(timer)
  }, [toast, closeToast])

  if (!toast) return null
  return (
    <div
      role="status"
      aria-label="Analysis complete"
      className="card animate-rise fixed top-20 right-4 left-4 z-50 flex items-start gap-3 p-4 sm:left-auto sm:w-96"
    >
      <span
        aria-hidden
        className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-full bg-brand font-mono text-sm font-bold text-on-brand"
      >
        {toast.score}
      </span>
      <div className="min-w-0 flex-1">
        <p className="font-display text-lg leading-tight text-ink">Analysis Complete!</p>
        <p className="mt-0.5 text-sm text-ink-soft">Your match score is ready ({toast.score}/100).</p>
        <Link
          to={`/match/${toast.jobId}`}
          onClick={closeToast}
          className="mt-2 inline-block text-sm font-semibold text-brand underline-offset-2 hover:underline"
        >
          Click to View Breakdown
        </Link>
      </div>
      <button
        type="button"
        onClick={closeToast}
        aria-label="Close notification"
        className="btn btn-ghost btn-sm -mt-1 -mr-2 px-2"
      >
        <svg viewBox="0 0 20 20" aria-hidden className="size-4">
          <path d="M5 5l10 10M15 5L5 15" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      </button>
    </div>
  )
}

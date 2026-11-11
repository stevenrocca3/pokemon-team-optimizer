import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { analysisEventsUrl, findAnalysisStatus, uploadBattleVideo } from '../api'
import type { AnalysisStatus } from '../types'
import { AnalysisJobContext, isFinal, type ActiveJob, type AnalysisJobState, type PastMatch } from './analysis-job'
import { loadJob, loadMatches, saveJob, saveMatches, withMatch } from './job-storage'

/** the log with this status line added, unless it's the line already showing (progress within one stage) */
function withLine(log: string[] = [], line: string): string[] {
  return log.at(-1) === line ? log : [...log, line]
}

/**
 * Follows the player's battle analysis from the app root, so it keeps going (and stays on screen) whichever page
 * they move to. The job's event stream stays open across page changes; the job is kept in localStorage, so a reload
 * reconnects to it.
 */
export function AnalysisJobProvider({ children }: { children: ReactNode }) {
  const [job, setJob] = useState<ActiveJob | null>(loadJob)
  const [pastMatches, setPastMatches] = useState<PastMatch[]>(loadMatches)
  const [toast, setToast] = useState<AnalysisJobState['toast']>(null)

  const jobRef = useRef(job)
  useEffect(() => {
    jobRef.current = job
    saveJob(job)
  }, [job])
  useEffect(() => saveMatches(pastMatches), [pastMatches])

  const apply = useCallback((status: AnalysisStatus) => {
    const current = jobRef.current
    if (!current || current.jobId !== status.job_id) return
    // the toast shows once per job: live, or on the first visit after it finished in a closed tab
    const announce = status.status === 'complete' && status.score !== null && !current.notified
    setJob((prev) =>
      prev && prev.jobId === status.job_id
        ? {
            ...prev,
            status: status.status,
            progress: status.progress,
            message: status.message,
            score: status.score,
            tier: status.tier,
            error: status.error,
            log: withLine(prev.log, status.status === 'failed' ? (status.error ?? status.message) : status.message),
            notified: prev.notified || announce,
          }
        : prev,
    )
    if (status.status === 'complete' && status.score !== null) {
      const match = {
        jobId: status.job_id,
        fileName: current.fileName,
        score: status.score,
        tier: status.tier,
        finishedAt: Date.now(),
      }
      setPastMatches((prev) => withMatch(prev, match))
    }
    if (announce) setToast({ jobId: status.job_id, score: status.score! })
  }, [])

  // one connection per running job, opened again only if the job changes (not on page changes)
  const following = job && !isFinal(job.status) ? job.jobId : null
  useEffect(() => {
    if (!following) return
    const source = new EventSource(analysisEventsUrl(following))
    const onStatus = (e: MessageEvent) => apply(JSON.parse(e.data) as AnalysisStatus)
    source.addEventListener('status', onStatus)
    // the stream dropped (EventSource retries by itself) or never opened: check the job still exists
    source.onerror = () => {
      findAnalysisStatus(following)
        .then((status) => (status ? apply(status) : setJob((prev) => (prev?.jobId === following ? null : prev))))
        .catch(() => {}) // offline: leave it to the stream's retry
    }
    return () => {
      source.removeEventListener('status', onStatus)
      source.close()
    }
  }, [following, apply])

  const start = useCallback(
    async (file: File) => {
      if (job && !isFinal(job.status)) {
        throw new Error('An analysis is already running. Wait for it to finish, then send the next one.')
      }
      const { job_id } = await uploadBattleVideo(file)
      setJob({
        jobId: job_id,
        fileName: file.name,
        status: 'queued',
        progress: 0,
        message: 'Waiting for a free worker...',
        score: null,
        tier: null,
        error: null,
        notified: false,
        log: ['Waiting for a free worker...'],
      })
    },
    [job],
  )

  const clear = useCallback(() => setJob(null), [])
  const closeToast = useCallback(() => setToast(null), [])

  const value = useMemo<AnalysisJobState>(
    () => ({
      job,
      start,
      clear,
      pastMatches,
      toast,
      closeToast,
    }),
    [job, start, clear, pastMatches, toast, closeToast],
  )
  return <AnalysisJobContext.Provider value={value}>{children}</AnalysisJobContext.Provider>
}

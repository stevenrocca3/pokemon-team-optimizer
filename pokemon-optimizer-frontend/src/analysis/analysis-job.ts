import { createContext, useContext } from 'react'
import type { AnalysisStage } from '../types'

/** The one analysis this browser is waiting on. Kept in localStorage, so a reload or a new tab picks it back up. */
export interface ActiveJob {
  jobId: string
  fileName: string
  status: AnalysisStage
  progress: number
  message: string
  score: number | null
  tier: string | null
  error: string | null
  /** the completion toast was shown (or the player already saw the result), so it isn't shown again */
  notified: boolean
}

/** A finished analysis, for View Past Matches. Newest first. */
export interface PastMatch {
  jobId: string
  fileName: string
  score: number
  tier: string | null
  finishedAt: number
}

export interface AnalysisJobState {
  job: ActiveJob | null
  /** uploads the video and starts following the job; rejects with a sentence for the player */
  start: (file: File) => Promise<void>
  /** removes the finished (or failed) job from the widget */
  clear: () => void
  pastMatches: PastMatch[]
  /** the job whose completion toast is showing */
  toast: { jobId: string; score: number } | null
  closeToast: () => void
}

export const AnalysisJobContext = createContext<AnalysisJobState>({
  job: null,
  start: async () => {},
  clear: () => {},
  pastMatches: [],
  toast: null,
  closeToast: () => {},
})

export function useAnalysisJob(): AnalysisJobState {
  return useContext(AnalysisJobContext)
}

export const isFinal = (stage: AnalysisStage) => stage === 'complete' || stage === 'failed'

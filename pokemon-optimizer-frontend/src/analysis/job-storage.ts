import type { AnalysisResult } from '../types'
import type { ActiveJob, PastMatch } from './analysis-job'

// Both live in this browser (guests included): the job being followed and the finished matches, newest first.
const JOB_KEY = 'dexpresso.analysis.job'
const MATCHES_KEY = 'dexpresso.analysis.matches'
const MAX_MATCHES = 30

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : fallback
  } catch {
    return fallback // storage blocked or the value is corrupt
  }
}

function write(key: string, value: unknown) {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // storage full or blocked: the job still runs, it just won't survive a reload
  }
}

export const loadJob = () => read<ActiveJob | null>(JOB_KEY, null)
export const saveJob = (job: ActiveJob | null) => write(JOB_KEY, job)
export const loadMatches = () => read<PastMatch[]>(MATCHES_KEY, [])

export const saveMatches = (matches: PastMatch[]) => write(MATCHES_KEY, matches)

/** The list with a finished match added at the front (once per job). */
export function withMatch(matches: PastMatch[], match: PastMatch): PastMatch[] {
  if (matches.some((m) => m.jobId === match.jobId)) return matches
  return [match, ...matches].slice(0, MAX_MATCHES)
}

// The server keeps results for a day; the browser keeps the last few it opened, so Past Matches still open after.
const RESULTS_KEY = 'dexpresso.analysis.results'
const resultKey = (jobId: string) => `dexpresso.analysis.result.${jobId}`
const MAX_RESULTS = 10

export const loadResult = (jobId: string) => read<AnalysisResult | null>(resultKey(jobId), null)

export function saveResult(jobId: string, result: AnalysisResult) {
  const kept = [jobId, ...read<string[]>(RESULTS_KEY, []).filter((id) => id !== jobId)]
  for (const old of kept.slice(MAX_RESULTS)) write(resultKey(old), null)
  write(RESULTS_KEY, kept.slice(0, MAX_RESULTS))
  write(resultKey(jobId), result)
}

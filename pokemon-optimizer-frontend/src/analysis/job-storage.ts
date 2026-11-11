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

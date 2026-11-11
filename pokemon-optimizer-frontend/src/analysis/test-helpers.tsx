import { act } from '@testing-library/react'
import { vi } from 'vitest'
import type { AnalysisStage, AnalysisStatus } from '../types'

export const JOB = 'a'.repeat(32)

/** Stands in for the browser's EventSource: records every connection and lets a test push `status` events. */
export class FakeEventSource {
  static all: FakeEventSource[] = []
  url: string
  closed = false
  onerror: ((e: Event) => void) | null = null
  private listeners = new Map<string, ((e: MessageEvent) => void)[]>()

  constructor(url: string) {
    this.url = url
    FakeEventSource.all.push(this)
  }

  addEventListener(type: string, fn: (e: MessageEvent) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), fn])
  }

  removeEventListener(type: string, fn: (e: MessageEvent) => void) {
    this.listeners.set(
      type,
      (this.listeners.get(type) ?? []).filter((f) => f !== fn),
    )
  }

  close() {
    this.closed = true
  }

  /** the server sends a status change */
  emit(status: AnalysisStatus) {
    act(() => {
      for (const fn of this.listeners.get('status') ?? [])
        fn(new MessageEvent('status', { data: JSON.stringify(status) }))
    })
  }

  /** the connection drops (or the server answered 404) */
  fail() {
    act(() => this.onerror?.(new Event('error')))
  }

  static latest(): FakeEventSource {
    return FakeEventSource.all[FakeEventSource.all.length - 1]
  }
}

export function status(stage: AnalysisStage, progress: number, extra: Partial<AnalysisStatus> = {}): AnalysisStatus {
  const messages: Record<AnalysisStage, string> = {
    queued: 'Waiting for a free worker...',
    processing_cv: 'Detecting Pokémon alignments...',
    reconstructing: 'Rebuilding the battle turn by turn...',
    auditing_turns: 'Auditing fast move turns...',
    complete: 'Analysis complete.',
    failed: 'Analysis failed.',
  }
  return {
    job_id: JOB,
    status: stage,
    progress,
    message: messages[stage],
    error: null,
    score: null,
    tier: null,
    created_at: 0,
    updated_at: 0,
    ...extra,
  }
}

export function json(body: unknown, statusCode = 200): Response {
  return new Response(JSON.stringify(body), {
    status: statusCode,
    headers: { 'Content-Type': 'application/json' },
  })
}

/**
 * fetch and EventSource replaced for a test: the upload answers 202 with JOB; `routes` can answer other URLs.
 * Returns the fetch mock so a test can look at the calls.
 */
export function fakeServer(routes: Record<string, () => Response> = {}) {
  FakeEventSource.all = []
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (routes[url]) return routes[url]()
    if (url === '/api/v1/analyze' && init?.method === 'POST') return json({ job_id: JOB, status: 'queued' }, 202)
    return json({ detail: 'not found' }, 404)
  })
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('EventSource', FakeEventSource)
  return fetchMock
}

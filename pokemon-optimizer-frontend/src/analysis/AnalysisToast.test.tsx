// @vitest-environment jsdom
// Issue #104: a toast on whichever page is open when the analysis finishes, one click from the result.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { AnalysisJobProvider } from './AnalysisJobProvider'
import { AnalysisToast } from './AnalysisToast'
import { AnalyzePage } from '../pages/AnalyzePage'
import { MatchPage } from '../pages/MatchPage'
import { FakeEventSource, JOB, fakeServer, json, status } from './test-helpers'

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>
}

const RESULT = {
  report: {
    match_id: JOB,
    score: 72,
    tier: { key: 'ace', name: 'Ace / Mid-Ladder Execution', min: 50 },
    findings: [],
    summary: {
      fatal_flaws: [],
      shield_audit: 'No shields used.',
      turn_efficiency: '',
    },
  },
  turn_log: {
    match_id: JOB,
    user_team: ['Swampert'],
    opponent_team: ['Togekiss'],
    cp: {},
    turn_log: [],
  },
}

function renderApp(path = '/analyze') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AnalysisJobProvider>
        <nav>
          <Link to="/team-builder">Teams</Link>
        </nav>
        <Routes>
          <Route path="/analyze" element={<AnalyzePage />} />
          <Route path="/match/:jobId" element={<MatchPage />} />
          <Route path="*" element={<Where />} />
        </Routes>
        <AnalysisToast />
      </AnalysisJobProvider>
    </MemoryRouter>,
  )
}

/** Uploads a video, then walks to the Team Builder, where the player is when the job finishes. */
async function startAndLeave() {
  const user = userEvent.setup()
  await user.upload(screen.getByLabelText(/battle recording/i), new File(['x'], 'set.mp4', { type: 'video/mp4' }))
  await user.click(screen.getByRole('link', { name: 'Teams' }))
  FakeEventSource.latest().emit(status('processing_cv', 80))
  return user
}

const toast = () => screen.queryByRole('status', { name: /analysis complete/i })
const complete = () =>
  FakeEventSource.latest().emit(status('complete', 100, { score: 72, tier: 'Ace / Mid-Ladder Execution' }))

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

describe('the completion toast', () => {
  // Nothing shows while the job runs; when it completes, the toast appears on the page the player is on (the Team
  // Builder here) with the score: "Analysis Complete! Your match score is ready (72/100)."
  it('appears on any page when the job completes, with the score', async () => {
    fakeServer()
    renderApp()
    await startAndLeave()
    expect(toast()).toBeNull()
    complete()
    expect(screen.getByTestId('where').textContent).toBe('/team-builder')
    expect(within(toast()!).getByText('Analysis Complete!')).toBeTruthy()
    expect(within(toast()!).getByText('Your match score is ready (72/100).')).toBeTruthy()
  })

  // One click goes straight to /match/<job_id>, which loads that job's result; the toast closes.
  it('takes the player straight to the result', async () => {
    fakeServer({ [`/api/v1/analyze/${JOB}/result`]: () => json(RESULT) })
    renderApp()
    const user = await startAndLeave()
    complete()
    await user.click(within(toast()!).getByRole('link', { name: 'Click to View Breakdown' }))
    expect(toast()).toBeNull()
    expect(await screen.findByText('72/100')).toBeTruthy()
    expect(screen.getByText('Ace / Mid-Ladder Execution')).toBeTruthy()
  })

  // The close button hides the toast and leaves the player where they are.
  it('can be closed without leaving the page', async () => {
    fakeServer()
    renderApp()
    const user = await startAndLeave()
    complete()
    await user.click(within(toast()!).getByRole('button', { name: 'Close notification' }))
    expect(toast()).toBeNull()
    expect(screen.getByTestId('where').textContent).toBe('/team-builder')
  })

  // Non-intrusive: left alone, it goes away by itself after 15 seconds.
  it('hides itself after 15 seconds', async () => {
    fakeServer()
    renderApp()
    await startAndLeave()
    vi.useFakeTimers()
    complete()
    act(() => vi.advanceTimersByTime(14_000))
    expect(toast()).not.toBeNull()
    act(() => vi.advanceTimersByTime(1_000))
    expect(toast()).toBeNull()
  })

  // A job that finished while the tab was closed still gets its toast when the player comes back (the stream's first
  // event is "complete"), but a toast already shown isn't shown again on the next reload.
  it('shows once per job, even across reloads', async () => {
    fakeServer()
    const first = renderApp()
    await startAndLeave()
    first.unmount()

    const second = renderApp('/')
    complete()
    expect(toast()).not.toBeNull()
    second.unmount()

    renderApp('/')
    expect(toast()).toBeNull()
  })

  // A failed job doesn't announce a score (the widget explains the failure).
  it('stays away when the job fails', async () => {
    fakeServer()
    renderApp()
    await startAndLeave()
    FakeEventSource.latest().emit(status('failed', 0, { error: 'No battle HUD found in this video.' }))
    expect(toast()).toBeNull()
    expect(screen.queryByText(/Analysis Complete!/)).toBeNull()
  })
})

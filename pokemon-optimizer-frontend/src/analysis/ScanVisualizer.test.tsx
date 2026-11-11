// @vitest-environment jsdom
// Issue #105: a radar scan and a live log of the real backend stages, for players who stay on /analyze.
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, Route, Routes } from 'react-router'
import { AnalysisJobProvider } from './AnalysisJobProvider'
import { AnalyzePage } from '../pages/AnalyzePage'
import { AnalysisWidget } from './AnalysisWidget'
import { FakeEventSource, JOB, fakeServer, status } from './test-helpers'

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/analyze']}>
      <AnalysisJobProvider>
        <nav>
          <Link to="/">Home</Link>
          <Link to="/analyze">Analyze</Link>
        </nav>
        <Routes>
          <Route path="/analyze" element={<AnalyzePage />} />
          <Route path="*" element={<p>elsewhere</p>} />
        </Routes>
        <AnalysisWidget />
      </AnalysisJobProvider>
    </MemoryRouter>,
  )
}

async function startJob() {
  const user = userEvent.setup()
  await user.upload(screen.getByLabelText(/battle recording/i), new File(['x'], 'set.mp4', { type: 'video/mp4' }))
  return user
}

const logLines = () => within(screen.getByRole('log', { name: 'Analysis log' })).getAllByRole('listitem')
const texts = () => logLines().map((li) => li.textContent?.trim())

/** The worker's real sequence: queued, the CV pass (several progress updates, one line), then each audit. */
function runStages() {
  const stream = FakeEventSource.latest()
  stream.emit(status('processing_cv', 10))
  stream.emit(status('processing_cv', 40))
  stream.emit(status('reconstructing', 90))
  stream.emit(status('auditing_turns', 92, { message: 'Auditing fast move turns...' }))
  stream.emit(status('auditing_turns', 94, { message: 'Evaluating shield decisions...' }))
}

beforeEach(() => localStorage.clear())
afterEach(cleanup)

describe('the scan visualizer on /analyze', () => {
  // Once a job is running, the drop zone gives way to the radar scan, labelled with how far it's got.
  it('replaces the drop zone with the radar while a job runs', async () => {
    fakeServer()
    renderApp()
    await startJob()
    FakeEventSource.latest().emit(status('processing_cv', 45))
    expect(screen.getByRole('img', { name: 'Scanning your battle: 45%' })).toBeTruthy()
    expect(screen.queryByLabelText(/battle recording/i)).toBeNull()
  })

  // The log lists each stage the backend reports, once, in order (progress updates within a stage don't repeat its
  // line); the newest line is marked as the current step.
  it('logs each real stage once, in order', async () => {
    fakeServer()
    renderApp()
    await startJob()
    runStages()
    expect(texts()).toEqual([
      'Waiting for a free worker...',
      'Detecting Pokémon alignments...',
      'Rebuilding the battle turn by turn...',
      'Auditing fast move turns...',
      'Evaluating shield decisions...',
    ])
    expect(logLines().map((li) => li.getAttribute('aria-current'))).toEqual([null, null, null, null, 'step'])
  })

  // Leaving /analyze and coming back keeps the log so far (it lives with the job, not the page).
  it('keeps the log when the player leaves and comes back', async () => {
    fakeServer()
    renderApp()
    const user = await startJob()
    runStages()
    await user.click(screen.getByRole('link', { name: 'Home' }))
    await user.click(screen.getByRole('link', { name: 'Analyze' }))
    expect(texts()).toHaveLength(5)
  })

  // When it completes, the log ends with "Analysis complete.", the score links to the breakdown, and "Analyze another
  // battle" brings the drop zone back.
  it('finishes with the score and a way to start again', async () => {
    fakeServer()
    renderApp()
    const user = await startJob()
    runStages()
    FakeEventSource.latest().emit(status('complete', 100, { score: 61, tier: 'Ace / Mid-Ladder Execution' }))
    expect(texts().at(-1)).toBe('Analysis complete.')
    expect(screen.getByRole('link', { name: 'See your 61/100 breakdown' }).getAttribute('href')).toBe(`/match/${JOB}`)
    await user.click(screen.getByRole('button', { name: 'Analyze another battle' }))
    expect(screen.getByLabelText(/battle recording/i)).toBeTruthy()
  })

  // A failure ends the log with the reason, and the drop zone can be brought back to try another video.
  it('ends the log with the reason when it fails', async () => {
    fakeServer()
    renderApp()
    const user = await startJob()
    FakeEventSource.latest().emit(status('failed', 0, { error: 'No battle HUD found in this video.' }))
    expect(texts().at(-1)).toBe('No battle HUD found in this video.')
    await user.click(screen.getByRole('button', { name: 'Analyze another battle' }))
    expect(screen.getByLabelText(/battle recording/i)).toBeTruthy()
  })

  // On /analyze the scan already shows everything, so the corner widget stays out of the way (it covered the page);
  // it comes back on every other page.
  it('keeps the corner widget off /analyze', async () => {
    fakeServer()
    renderApp()
    const user = await startJob()
    expect(screen.queryByRole('region', { name: /battle analysis/i })).toBeNull()
    await user.click(screen.getByRole('link', { name: 'Home' }))
    expect(screen.getByRole('region', { name: /battle analysis/i })).toBeTruthy()
  })
})

// @vitest-environment jsdom
// Issue #103: the analysis keeps running, and stays visible, while the player uses the rest of the site.
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { AnalysisJobProvider } from './AnalysisJobProvider'
import { AnalysisWidget } from './AnalysisWidget'
import { AnalyzePage } from '../pages/AnalyzePage'
import { MatchesPage } from '../pages/MatchesPage'
import { FakeEventSource, JOB, fakeServer, json, status } from './test-helpers'

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>
}

/** The app's shape: the provider at the root, pages swapping under it, the widget beside them. */
function renderApp(path = '/analyze') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AnalysisJobProvider>
        <nav>
          <Link to="/">Home</Link>
        </nav>
        <Routes>
          <Route path="/analyze" element={<AnalyzePage />} />
          <Route path="/matches" element={<MatchesPage />} />
          <Route path="*" element={<Where />} />
        </Routes>
        <AnalysisWidget />
      </AnalysisJobProvider>
    </MemoryRouter>,
  )
}

const video = () => new File(['not really a video'], 'gbl-set-3.mp4', { type: 'video/mp4' })

/** Uploads a video on /analyze, then (if it was accepted) steps to the home page: the widget stays off /analyze. */
async function uploadVideo(user = userEvent.setup()) {
  await user.upload(screen.getByLabelText(/battle recording/i), video())
  if (FakeEventSource.all.length > 0) await user.click(screen.getByRole('link', { name: 'Home' }))
  return user
}

const widget = () => screen.getByRole('region', { name: /battle analysis/i })

beforeEach(() => localStorage.clear())
afterEach(cleanup)

describe('AnalysisJobProvider and the floating widget', () => {
  // Choosing a video on /analyze uploads it (POST /api/v1/analyze with the file), opens the job's event stream, and
  // the widget appears in the corner: "Analyzing Gameplay: 0% complete..." while it's queued.
  it('starts a job from the upload and shows it in the widget', async () => {
    const fetchMock = fakeServer()
    renderApp()
    await uploadVideo()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/analyze')
    expect((init?.body as FormData).get('file')).toMatchObject({
      name: 'gbl-set-3.mp4',
    })
    expect(FakeEventSource.all.map((s) => s.url)).toEqual([`/api/v1/analyze/${JOB}/events`])
    expect(await within(widget()).findByText('Analyzing Gameplay: 0% complete...')).toBeTruthy()
  })

  // Each status the worker pushes moves the widget on: 45% and the stage's status line.
  it('follows the progress the server pushes', async () => {
    fakeServer()
    renderApp()
    await uploadVideo()
    FakeEventSource.latest().emit(status('processing_cv', 45))
    expect(within(widget()).getByText('Analyzing Gameplay: 45% complete...')).toBeTruthy()
    expect(within(widget()).getByText('Detecting Pokémon alignments...')).toBeTruthy()
  })

  // Leaving the page keeps the job: the widget is still there on the home page, the same single connection is still
  // open (not closed and reopened), and progress keeps arriving.
  it('keeps the job and its connection across page changes', async () => {
    fakeServer()
    renderApp()
    const user = await uploadVideo()
    FakeEventSource.latest().emit(status('processing_cv', 30))
    await user.click(screen.getByRole('link', { name: 'Home' }))
    expect(screen.getByTestId('where').textContent).toBe('/')
    expect(FakeEventSource.all).toHaveLength(1)
    expect(FakeEventSource.latest().closed).toBe(false)
    FakeEventSource.latest().emit(status('processing_cv', 60))
    expect(within(widget()).getByText('Analyzing Gameplay: 60% complete...')).toBeTruthy()
  })

  // A reload (the app mounting again) picks the job back up: the widget shows where it was and reconnects to the
  // same job's stream.
  it('picks the job back up after a reload', async () => {
    fakeServer()
    const first = renderApp()
    await uploadVideo()
    FakeEventSource.latest().emit(status('processing_cv', 45))
    first.unmount()
    expect(FakeEventSource.latest().closed).toBe(true)

    renderApp('/')
    expect(within(widget()).getByText('Analyzing Gameplay: 45% complete...')).toBeTruthy()
    expect(FakeEventSource.all).toHaveLength(2)
    expect(FakeEventSource.latest().url).toBe(`/api/v1/analyze/${JOB}/events`)
  })

  // A job the server no longer knows (results expire after a day) is dropped when its stream fails and the status
  // check says 404, instead of showing progress forever.
  it('drops a job the server has forgotten', async () => {
    fakeServer()
    const first = renderApp()
    await uploadVideo()
    first.unmount()
    renderApp('/')
    FakeEventSource.latest().fail()
    expect(await screen.findByTestId('where')).toBeTruthy()
    await expect.poll(() => screen.queryByRole('region', { name: /battle analysis/i })).toBeNull()
  })

  // While it runs, the widget offers somewhere to go: Build Teams While You Wait (/team-builder) and View Past Matches
  // (/matches). Following one keeps the widget showing.
  it('offers the two redirect buttons', async () => {
    fakeServer()
    renderApp()
    const user = await uploadVideo()
    const teams = within(widget()).getByRole('link', {
      name: 'Build Teams While You Wait',
    })
    expect(teams.getAttribute('href')).toBe('/team-builder')
    expect(within(widget()).getByRole('link', { name: 'View Past Matches' }).getAttribute('href')).toBe('/matches')
    await user.click(teams)
    expect(screen.getByTestId('where').textContent).toBe('/team-builder')
    expect(widget()).toBeTruthy()
  })

  // The widget collapses to just its progress line and expands again; the toggle says which state it's in.
  it('collapses and expands', async () => {
    fakeServer()
    renderApp()
    const user = await uploadVideo()
    FakeEventSource.latest().emit(status('processing_cv', 45))
    const toggle = within(widget()).getByRole('button', {
      name: /hide details/i,
    })
    await user.click(toggle)
    expect(toggle.getAttribute('aria-expanded')).toBe('false')
    expect(within(widget()).queryByRole('link', { name: 'View Past Matches' })).toBeNull()
    expect(within(widget()).getByText('Analyzing Gameplay: 45% complete...')).toBeTruthy()
    await user.click(within(widget()).getByRole('button', { name: /show details/i }))
    expect(within(widget()).getByRole('link', { name: 'View Past Matches' })).toBeTruthy()
  })

  // When the job completes the widget stays (with the score and a link to the breakdown) until the player clears it;
  // the stream is closed. Cleared, it stays gone after a reload, and the match is listed under Past Matches.
  it('stays until cleared, and keeps the finished match', async () => {
    fakeServer()
    const first = renderApp()
    const user = await uploadVideo()
    FakeEventSource.latest().emit(
      status('complete', 100, {
        score: 72,
        tier: 'Ace / Mid-Ladder Execution',
      }),
    )
    expect(FakeEventSource.latest().closed).toBe(true)
    expect(within(widget()).getByText('Analysis complete: 72/100')).toBeTruthy()
    expect(within(widget()).getByRole('link', { name: 'View breakdown' }).getAttribute('href')).toBe(`/match/${JOB}`)
    await user.click(within(widget()).getByRole('button', { name: 'Dismiss' }))
    expect(screen.queryByRole('region', { name: /battle analysis/i })).toBeNull()

    first.unmount()
    renderApp('/matches')
    expect(screen.queryByRole('region', { name: /battle analysis/i })).toBeNull()
    const row = screen.getByRole('link', { name: /gbl-set-3\.mp4/ })
    expect(row.getAttribute('href')).toBe(`/match/${JOB}`)
    expect(within(row).getByText('72/100')).toBeTruthy()
  })

  // A failed job shows the reader's own reason in the widget, until dismissed.
  it('shows why a job failed', async () => {
    fakeServer()
    renderApp()
    await uploadVideo()
    FakeEventSource.latest().emit(status('failed', 0, { error: 'No battle HUD found in this video.' }))
    expect(within(widget()).getByText('No battle HUD found in this video.')).toBeTruthy()
    expect(within(widget()).getByRole('button', { name: 'Dismiss' })).toBeTruthy()
  })

  // An upload the server refuses (415, not a video) shows its sentence on the page and starts nothing.
  it('shows a refused upload on the page', async () => {
    fakeServer({
      '/api/v1/analyze': () =>
        json(
          {
            detail: 'That file isn’t a video we can read. Upload an MP4 or MOV screen recording.',
          },
          415,
        ),
    })
    renderApp()
    await uploadVideo()
    expect(await screen.findByText(/Upload an MP4 or MOV screen recording/)).toBeTruthy()
    expect(screen.queryByRole('region', { name: /battle analysis/i })).toBeNull()
    expect(FakeEventSource.all).toHaveLength(0)
  })
})

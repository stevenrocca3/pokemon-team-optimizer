// @vitest-environment jsdom
// Issue #124: /analytics, the player's dashboard, and what a logged-out visitor sees instead.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { AnalyticsPage } from './AnalyticsPage'
import { FakeEventSource, JOB, json, status } from '../analysis/test-helpers'
import { AnalysisJobProvider } from '../analysis/AnalysisJobProvider'
import { playerAnalytics } from '../analysis/analytics-fixture'
import type { AnalyticsSummary } from '../types'

let fetchMock: ReturnType<typeof vi.fn>

function renderWith(summary: AnalyticsSummary | (() => Response)) {
  fetchMock = vi.fn(async (url: string) => {
    if (url === '/api/v1/analytics/summary') return typeof summary === 'function' ? summary() : json(summary)
    return json({ detail: 'not found' }, 404)
  })
  vi.stubGlobal('fetch', fetchMock)
  return render(
    <MemoryRouter>
      <AnalyticsPage />
    </MemoryRouter>,
  )
}

const GUEST: AnalyticsSummary = { access: 'guest', loginUrl: '/oauth2/authorization/google' }

beforeEach(() => localStorage.clear())
afterEach(cleanup)

describe('AnalyticsPage', () => {
  // Logged out: the call to action "Login to receive catered battle analytics", with a one-click Google login, over
  // a preview of the dashboard that's clearly marked as an example. None of a player's own sections are there.
  it('shows a guest the login call to action over an example preview', async () => {
    renderWith(GUEST)
    expect(await screen.findByRole('heading', { name: 'Login to receive catered battle analytics' })).toBeTruthy()
    expect(screen.getByRole('link', { name: /continue with google/i }).getAttribute('href')).toBe(
      '/oauth2/authorization/google',
    )
    expect(screen.getByText('Example')).toBeTruthy()
    expect(screen.queryByRole('region', { name: 'Your trend' })).toBeNull()
    expect(screen.queryByRole('list', { name: 'Your battles' })).toBeNull()
  })

  // Logged in and improving: the trend card says Improving with the +10.0 velocity, the rolling averages and the
  // summary naming what got better; the green upward indicator is marked as improving.
  it('shows an improving trend', async () => {
    renderWith(playerAnalytics())
    const trend = await screen.findByRole('region', { name: 'Your trend' })
    expect(within(trend).getByText('Improving')).toBeTruthy()
    expect(within(trend).getByText('+10.0')).toBeTruthy()
    expect(within(trend).getByText(/Biggest gain: switch mistakes/)).toBeTruthy()
    expect(within(trend).getByText('75.0')).toBeTruthy() // last 10
    expect(trend.querySelector('[data-direction="IMPROVING"]')).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'Login to receive catered battle analytics' })).toBeNull()
  })

  // Declining: marked as a decline, with the velocity and the summary's biggest cause.
  it('shows a declining trend with its cause', async () => {
    renderWith(
      playerAnalytics({
        trend: {
          direction: 'DECLINING',
          velocity: -27,
          recentAverage: 61,
          earlierAverage: 88,
          rolling: { last5: 61, last10: 74.5, last25: null },
          focus: 'timing',
          summary:
            'Down 27.0 points: your last 5 battles average 61.0, against 88.0 in the 15 before. Biggest cause: timing errors (charged moves thrown on the opponent’s alignment), 2.0 per battle now against 0.0 before.',
        },
      }),
    )
    const trend = await screen.findByRole('region', { name: 'Your trend' })
    expect(within(trend).getByText('Declining')).toBeTruthy()
    expect(within(trend).getByText('−27.0')).toBeTruthy()
    expect(within(trend).getByText(/Biggest cause: timing errors/)).toBeTruthy()
    expect(trend.querySelector('[data-direction="DECLINING"]')).toBeTruthy()
  })

  // Under 10 battles there's no trend yet: the card says how many more battles it needs, and shows no velocity.
  it('says how many battles a trend needs', async () => {
    renderWith(
      playerAnalytics({
        battles: 7,
        trend: {
          direction: 'NOT_ENOUGH_DATA',
          velocity: null,
          recentAverage: null,
          earlierAverage: null,
          rolling: { last5: 70, last10: null, last25: null },
          focus: null,
          summary: 'Trends start at 10 battles: 3 more battles to go.',
        },
      }),
    )
    const trend = await screen.findByRole('region', { name: 'Your trend' })
    expect(within(trend).getByText('Trends start at 10 battles: 3 more battles to go.')).toBeTruthy()
    expect(trend.querySelector('[data-direction]')).toBeNull()
  })

  // The top teams, best first: each with its battles, record, average score, win rate and rank score.
  it('lists the top teams best first', async () => {
    renderWith(playerAnalytics())
    const teams = await screen.findByRole('list', { name: 'Your top teams' })
    const items = within(teams).getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0].textContent).toContain('Swampert / Cradily / Clodsire')
    expect(items[0].textContent).toContain('7 battles')
    expect(items[0].textContent).toContain('5W 1L')
    expect(items[0].textContent).toContain('77.0')
    expect(items[1].textContent).toContain('Medicham / Lickitung / Noctowl')
  })

  // The history lists each saved battle, newest first, linking to its review (/match/ + the job id, no dashes).
  it('links each saved battle to its review', async () => {
    renderWith(playerAnalytics())
    const battles = await screen.findByRole('list', { name: 'Your battles' })
    const links = within(battles).getAllByRole('link')
    expect(links).toHaveLength(2)
    expect(links[0].getAttribute('href')).toBe('/match/0123456789ab4def8123456789abcd01')
    expect(links[0].textContent).toContain('88')
    expect(links[1].textContent).toContain('Win')
  })

  // A logged-in player with nothing saved yet is pointed at the upload page.
  it('points a player with no battles at the upload', async () => {
    renderWith(playerAnalytics({ battles: 0, history: [], topTeams: [] }))
    expect(await screen.findByText(/No battles saved yet/)).toBeTruthy()
    expect(screen.getByRole('link', { name: /analyze a battle/i }).getAttribute('href')).toBe('/analyze')
  })

  // Issue #125: the lock lifts by itself. The dashboard is open at 9 of 10 when the 10th battle finishes; once it's
  // saved to the history, the dashboard asks for the summary again and the rank comes out unlocked.
  it('unlocks the rank when the 10th battle is saved', async () => {
    vi.stubGlobal('matchMedia', (query: string) => ({ matches: false, media: query }))
    localStorage.setItem(
      'dexpresso.analysis.job',
      JSON.stringify({
        jobId: JOB,
        fileName: 'set.mp4',
        status: 'auditing_turns',
        progress: 94,
        message: '',
        score: null,
        tier: null,
        error: null,
        notified: false,
      }),
    )
    let summaries = 0
    const locked = playerAnalytics({ battles: 9, rank: { locked: true, battles: 9, required: 10, remaining: 1 } })
    const unlocked = playerAnalytics({ battles: 10 })
    FakeEventSource.all = []
    vi.stubGlobal('EventSource', FakeEventSource)
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url === '/api/v1/analytics/summary') return json(summaries++ === 0 ? locked : unlocked)
        if (url === '/api/v1/battles') return new Response(null, { status: 201 })
        return json({ detail: 'not found' }, 404)
      }),
    )
    render(
      <MemoryRouter>
        <AnalysisJobProvider>
          <AnalyticsPage />
        </AnalysisJobProvider>
      </MemoryRouter>,
    )
    expect(await screen.findByText('9 / 10 battles analyzed — 1 more required')).toBeTruthy()

    FakeEventSource.latest().emit(status('complete', 100, { score: 80 }))

    expect(await screen.findByText('Ace / Mid-Ladder Execution')).toBeTruthy()
    expect(screen.queryByText('Submit more battle videos to receive your score')).toBeNull()
    expect(summaries).toBe(2)
  })

  // If the summary can't be loaded, the page says so instead of staying blank.
  it('says when the analytics can’t be loaded', async () => {
    renderWith(() => json({ detail: 'Something went wrong on our side.' }, 500))
    expect((await screen.findByRole('alert')).textContent).toContain('Something went wrong')
  })
})

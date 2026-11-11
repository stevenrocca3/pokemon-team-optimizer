// @vitest-environment jsdom
// Issue #107: the score hero, its tier badge, and the blunt critique cards.
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { CritiqueCards, ScoreHero } from './MatchReview'
import { sampleResult } from './match-fixture'
import { MatchPage } from '../pages/MatchPage'
import { CollectionContext, type CollectionState } from '../collection/collection-context'
import { fakeServer, json } from './test-helpers'
import type { MatchReport } from '../types'

const TIERS: MatchReport['tier'][] = [
  { key: 'legend', name: 'Legend Tier Execution', min: 90 },
  { key: 'veteran', name: 'Veteran / Expert Tier Execution', min: 75 },
  { key: 'ace', name: 'Ace / Mid-Ladder Execution', min: 50 },
  { key: 'critical', name: 'Critical Execution Flaws (Needs Major Rebuilding)', min: 0 },
]

/** A battle with nothing wrong in it. */
function cleanReport(): MatchReport {
  const r = sampleResult().report
  return {
    ...r,
    score: 100,
    tier: TIERS[0],
    findings: [],
    counts: { timing_errors: 0, mismanaged_shields: 0, suboptimal_switches: 0, suboptimal_charges: 0 },
    deductions: { timing_errors: 0, mismanaged_shields: 0, suboptimal_switches: 0, suboptimal_charges: 0 },
    summary: {
      fatal_flaws: ['No execution errors detected.'],
      shield_audit: 'No shields used.',
      turn_efficiency:
        'Turns lost to suboptimal timing: 0 (0 free fast moves given to the opponent). Off-alignment throws: 1 of 1.',
    },
    metrics: { ...r.metrics, free_turns_given: 0, off_alignment_throws: 1, shields_used: 0 },
  }
}

const section = (name: string) => screen.getByRole('region', { name })
const bullets = (name: string) =>
  within(section(name))
    .getAllByRole('listitem')
    .map((li) => li.textContent)

beforeEach(() => localStorage.clear())
afterEach(cleanup)

describe('ScoreHero', () => {
  // The score out of 100 (for screen readers as a sentence; the digits count up on screen) and the tier as a badge.
  it('shows the score and the tier badge', () => {
    render(<ScoreHero report={sampleResult().report} />)
    expect(screen.getByLabelText('Score: 73 out of 100')).toBeTruthy()
    const badge = screen.getByText('Ace / Mid-Ladder Execution')
    expect(badge.dataset.tier).toBe('ace')
  })

  // Each tier has its own colour: four tiers, four different badge styles.
  it('colour-codes each tier', () => {
    const styles = TIERS.map((tier) => {
      const { unmount } = render(<ScoreHero report={{ ...sampleResult().report, tier }} />)
      const cls = screen.getByText(tier.name).className
      unmount()
      return cls
    })
    expect(new Set(styles).size).toBe(4)
  })

  // Where the points went: one line per category that cost something (12 for timing, 15 for a shield), none for
  // categories that cost nothing.
  it('breaks down the deductions', () => {
    render(<ScoreHero report={sampleResult().report} />)
    const lines = within(screen.getByRole('list', { name: 'Deductions' }))
      .getAllByRole('listitem')
      .map((li) => li.textContent)
    expect(lines).toEqual(['−12Fast move timing×1', '−15Shields×1'])
  })
})

describe('CritiqueCards', () => {
  // Fatal Flaws: the report's own sentences, costliest first, as bullets.
  it('lists the fatal flaws', () => {
    render(<CritiqueCards report={sampleResult().report} />)
    expect(bullets('Fatal Flaws')).toEqual([
      'Turn 30: wasted a shield on Body Slam (resisted, 70% HP left)',
      'Turn 16: Threw Charge Move on 2-turn alignment (+1 free Mud Shot given to opponent)',
    ])
  })

  // Shield Audit: the one-line verdict, then each wasted shield's own sentence.
  it('audits the shields', () => {
    render(<CritiqueCards report={sampleResult().report} />)
    expect(within(section('Shield Audit')).getByText('Used 1 shield, wasted 1.')).toBeTruthy()
    expect(bullets('Shield Audit')).toEqual(['Turn 30: wasted a shield on Body Slam (resisted, 70% HP left)'])
  })

  // Fast Move Turn Efficiency: plain metric lines, e.g. "Turns lost to suboptimal timing: 2".
  it('states the turn metrics plainly', () => {
    render(<CritiqueCards report={sampleResult().report} />)
    expect(bullets('Fast Move Turn Efficiency')).toEqual([
      'Turns lost to suboptimal timing: 2',
      'Off-alignment throws: 0 of 1',
      'Turns lost to switch lag: 0',
    ])
  })

  // A clean battle says so in the report's own words, and nothing anywhere softens or pads the verdicts: no praise,
  // no hedging.
  it('stays blunt, with no sugar-coating', () => {
    const { container } = render(
      <>
        <ScoreHero report={cleanReport()} />
        <CritiqueCards report={cleanReport()} />
      </>,
    )
    expect(bullets('Fatal Flaws')).toEqual(['No execution errors detected.'])
    expect(within(section('Shield Audit')).getByText('No shields used.')).toBeTruthy()
    expect(container.textContent).not.toMatch(/great|nice|awesome|well done|good job|maybe|perhaps|might want/i)
  })
})

describe('MatchPage', () => {
  // The breakdown page puts it together: score hero, critique cards and the timeline.
  it('shows the whole review', async () => {
    fakeServer({ '/api/v1/analyze/m1/result': () => json(sampleResult()) })
    render(
      <MemoryRouter initialEntries={['/match/m1']}>
        <Routes>
          <Route path="/match/:jobId" element={<MatchPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByLabelText('Score: 73 out of 100')).toBeTruthy()
    expect(section('Fatal Flaws')).toBeTruthy()
    expect(screen.getByRole('img', { name: 'Your HP' })).toBeTruthy()
  })

  // Issue #124: a logged-out player who finishes a battle is told what logging in gets them, under the review; a
  // logged-in one isn't.
  it('asks a logged-out player to log in for their analytics', async () => {
    const page = (storage: 'browser' | 'account') =>
      render(
        <CollectionContext.Provider value={{ status: 'ready', storage } as CollectionState}>
          <MemoryRouter initialEntries={['/match/m1']}>
            <Routes>
              <Route path="/match/:jobId" element={<MatchPage />} />
            </Routes>
          </MemoryRouter>
        </CollectionContext.Provider>,
      )
    fakeServer({ '/api/v1/analyze/m1/result': () => json(sampleResult()) })
    const guest = page('browser')
    await screen.findByLabelText('Score: 73 out of 100')
    expect(screen.getByRole('heading', { name: 'Login to receive catered battle analytics' })).toBeTruthy()
    expect(screen.getByRole('link', { name: /continue with google/i })).toBeTruthy()
    guest.unmount()

    page('account')
    await screen.findByLabelText('Score: 73 out of 100')
    expect(screen.queryByRole('heading', { name: 'Login to receive catered battle analytics' })).toBeNull()
  })

  // Issue #124: a battle the analysis service has forgotten, opened in a browser that never cached it (the history
  // on /analytics, another device), comes from the player's saved history instead.
  it('opens a forgotten battle from the saved history', async () => {
    const JOB = '0123456789ab4def8123456789abcd01'
    const fetchMock = fakeServer({
      [`/api/v1/analyze/${JOB}/result`]: () => json({ detail: 'This analysis has expired.' }, 404),
      '/api/v1/analytics/battles/01234567-89ab-4def-8123-456789abcd01': () => json(sampleResult()),
    })
    render(
      <MemoryRouter initialEntries={[`/match/${JOB}`]}>
        <Routes>
          <Route path="/match/:jobId" element={<MatchPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByLabelText('Score: 73 out of 100')).toBeTruthy()
    expect(fetchMock.mock.calls.map(([url]) => url)).toContain(
      '/api/v1/analytics/battles/01234567-89ab-4def-8123-456789abcd01',
    )
  })

  // The server keeps results for a day; the browser keeps the ones it has opened, so a past match still opens after
  // the server has forgotten it.
  it('still opens a past match after the server has forgotten it', async () => {
    const page = () =>
      render(
        <MemoryRouter initialEntries={['/match/m1']}>
          <Routes>
            <Route path="/match/:jobId" element={<MatchPage />} />
          </Routes>
        </MemoryRouter>,
      )
    fakeServer({ '/api/v1/analyze/m1/result': () => json(sampleResult()) })
    const first = page()
    await screen.findByLabelText('Score: 73 out of 100')
    first.unmount()

    fakeServer({ '/api/v1/analyze/m1/result': () => json({ detail: 'This analysis has expired.' }, 404) })
    page()
    expect(await screen.findByLabelText('Score: 73 out of 100')).toBeTruthy()
  })
})

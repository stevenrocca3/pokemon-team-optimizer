// @vitest-environment jsdom
// Issue #108: a team that scores under 50% against the meta gets a warning card that imports it into the Team Builder.
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { TeamNudge } from './TeamNudge'
import { leagueFor, speciesIdFor } from './team-nudge'
import { sampleResult } from './match-fixture'
import { fakeServer, json } from './test-helpers'
import { TeamBuilderPage } from '../pages/TeamBuilderPage'
import type { AnalysisResult, Suggestion } from '../types'
import { suggestion } from '../components/suggestion-fixture'

/** The sample battle, with all three of your Pokémon seen: Swampert, Cradily and Clodsire in Great League. */
function fullTeam(): AnalysisResult {
  const result = sampleResult()
  result.turn_log.user_team = ['Swampert', 'Cradily', 'Clodsire']
  result.turn_log.cp = { 'user:Swampert': 1497, 'user:Cradily': 1499, 'user:Clodsire': 1490, 'opp:Togekiss': 1498 }
  return result
}

/** what the Team Builder answers: one team (the three species), winning `rate` of its battles */
function teamResult(rate: number, suggestions: Suggestion[] = []) {
  return () =>
    json({
      league: 'GREAT',
      metaSize: 60,
      metaDate: '2026-11-09',
      eligible: 3,
      skipped: [],
      cup: null,
      teams: [{ members: [], winRate: { rate, low: rate - 0.05, high: rate + 0.05 }, toughest: [], suggestions }],
    })
}

function renderReview(result: AnalysisResult) {
  return render(
    <MemoryRouter initialEntries={['/match/m1']}>
      <Routes>
        <Route path="/match/:jobId" element={<TeamNudge result={result} />} />
        <Route path="/team-builder" element={<TeamBuilderPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

const card = () => screen.queryByRole('region', { name: /team/i })
const teamRequests = (fetchMock: ReturnType<typeof fakeServer>) =>
  fetchMock.mock.calls.filter(([url]) => url === '/api/team-builder').map(([, init]) => JSON.parse(String(init?.body)))

beforeEach(() => localStorage.clear())
afterEach(cleanup)

describe('team nudge helpers', () => {
  // The names the reader saw become PvPoke species IDs, regional forms included.
  it('turns detected names into species IDs', () => {
    expect(speciesIdFor('Swampert')).toBe('swampert')
    expect(speciesIdFor('Weezing (Galarian)')).toBe('weezing_galarian')
    expect(speciesIdFor('Mr. Mime')).toBe('mr_mime')
  })

  // The league comes from your Pokémon's CP (the opponent's doesn't count): up to 1500 is Great, up to 2500 Ultra.
  it('works out the league from your CP', () => {
    expect(leagueFor({ 'user:Swampert': 1497, 'opp:Dialga': 3500 })).toBe('GREAT')
    expect(leagueFor({ 'user:Swampert': 2490 })).toBe('ULTRA')
    expect(leagueFor({ 'user:Swampert': 3100 })).toBe('MASTER')
  })
})

describe('TeamNudge', () => {
  // The detected three go to the Team Builder (as species, Great League); winning 42% of meta battles is under 50%,
  // so the warning card appears with the number.
  it('warns when the detected team scores under 50%', async () => {
    const fetchMock = fakeServer({ '/api/team-builder': teamResult(0.42) })
    renderReview(fullTeam())
    expect(await screen.findByRole('heading', { name: 'Critical Team Vulnerability Detected' })).toBeTruthy()
    expect(card()!.textContent).toContain('won 42% of its battles against the current Great League meta')
    expect(teamRequests(fetchMock)).toEqual([
      {
        league: 'GREAT',
        pokemon: [],
        cup: null,
        species: [
          { speciesId: 'swampert', shadow: false },
          { speciesId: 'cradily', shadow: false },
          { speciesId: 'clodsire', shadow: false },
        ],
      },
    ])
  })

  // A team that wins 58% gets no warning.
  it('stays quiet for a team that holds up', async () => {
    const fetchMock = fakeServer({ '/api/team-builder': teamResult(0.58) })
    renderReview(fullTeam())
    await expect.poll(() => teamRequests(fetchMock).length).toBe(1)
    await new Promise((r) => setTimeout(r, 20))
    expect(card()).toBeNull()
  })

  // If the Team Builder can't answer, the review just goes without the card.
  it('stays quiet when the Team Builder fails', async () => {
    const fetchMock = fakeServer({ '/api/team-builder': () => json({ detail: 'busy' }, 503) })
    renderReview(fullTeam())
    await expect.poll(() => teamRequests(fetchMock).length).toBe(1)
    await new Promise((r) => setTimeout(r, 20))
    expect(card()).toBeNull()
  })

  // "Import Team to Dexpresso Team Builder" opens the Team Builder with the detected three as the Pokémon and the
  // battle's league picked; building there sends exactly that team.
  it('imports the team into the Team Builder', async () => {
    const fetchMock = fakeServer({ '/api/team-builder': teamResult(0.42) })
    const user = userEvent.setup()
    renderReview(fullTeam())
    await user.click(await screen.findByRole('button', { name: 'Import Team to Dexpresso Team Builder' }))
    expect(screen.getByText(/Swampert, Cradily, Clodsire/)).toBeTruthy()
    expect(screen.getByText('3 Pokémon ready')).toBeTruthy()
    expect(screen.getByRole('tab', { name: /Great/ }).getAttribute('aria-selected')).toBe('true')
    await user.click(screen.getByRole('button', { name: 'Build teams' }))
    const [, built] = teamRequests(fetchMock)
    expect(built.species.map((s: { speciesId: string }) => s.speciesId)).toEqual(['swampert', 'cradily', 'clodsire'])
  })

  // Only two of your Pokémon came out (the battle ended first), so there's no team to score: no request, and a plain
  // card offers to import the two seen, without a warning.
  it('offers the import without a score when fewer than 3 were seen', async () => {
    const fetchMock = fakeServer()
    const result = fullTeam()
    result.turn_log.user_team = ['Swampert', 'Cradily']
    renderReview(result)
    expect(await screen.findByText(/Only Swampert and Cradily came out/)).toBeTruthy()
    expect(within(card()!).getByRole('button', { name: 'Import Team to Dexpresso Team Builder' })).toBeTruthy()
    expect(screen.queryByText('Critical Team Vulnerability Detected')).toBeNull()
    expect(teamRequests(fetchMock)).toEqual([])
  })

  // #112: the warning card also lists the Team Builder's 5 swaps for the team, so the fix is right there
  it('lists the swaps for a team under 50%', async () => {
    const swaps = ['medicham', 'registeel', 'furret', 'altaria', 'carbink'].map((id) => suggestion(id, 'LEAD'))
    fakeServer({ '/api/team-builder': teamResult(0.42, swaps) })
    renderReview(fullTeam())
    const warning = await screen.findByRole('region', { name: /Critical/ })
    const lists = within(warning).queryAllByRole('list', { name: 'Swaps for this team' })
    expect(lists).toHaveLength(1)
    expect(within(lists[0]).getAllByRole('listitem')).toHaveLength(5)
  })
})

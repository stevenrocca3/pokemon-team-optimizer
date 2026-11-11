// @vitest-environment jsdom
// Issue #118: "Which One to Build" is easy to find: in the nav, on its own page, on the Team Builder, with a
// side-by-side "Build This One" card, and as a nudge in match reviews.
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import type { ReactNode } from 'react'
import { SpeciesProvider } from '../species/SpeciesProvider'
import { CollectionProvider } from '../collection/CollectionProvider'
import { fakeServer, json } from '../analysis/test-helpers'
import { sampleResult } from '../analysis/match-fixture'
import { Header } from '../components/Header'
import { BuildCallout } from '../components/BuildCallout'
import { CompareCopies } from '../components/CompareCopies'
import { BuildNudge } from '../analysis/BuildNudge'
import { BuildPage } from './BuildPage'
import type { CaughtPokemon, IvComparisonResult } from '../types'

const copy = (id: number, speciesId: string, atkIv: number, defIv = 15): CaughtPokemon => ({
  id,
  speciesId,
  atkIv,
  defIv,
  staIv: 15,
  level: null,
  fastMove: 'COUNTER',
  chargedMoves: ['ICE_PUNCH'],
  shadow: false,
})

/** a guest's box: two Medicham, one Swampert */
const BOX = [copy(1, 'medicham', 0), copy(2, 'medicham', 4, 14), copy(3, 'swampert', 1)]

/** copy 2 (4/14/15) wins 60 of 90 meta battles, copy 1 (0/15/15) wins 49 */
const COMPARED: IvComparisonResult = {
  league: 'GREAT',
  metaSize: 45,
  metaDate: '2026-11-09',
  pokemon: [
    { index: 1, level: 49, cp: 1499, wins: 60, battles: 90, metaRating: 600, problem: null },
    { index: 0, level: 50, cp: 1498, wins: 49, battles: 90, metaRating: 550, problem: null },
  ],
  flips: [],
}

function serve() {
  return fakeServer({
    '/api/collection': () => json({ detail: 'log in' }, 401),
    '/api/checklist': () => json({ detail: 'log in' }, 401),
    '/api/species': () =>
      json([
        { id: 'medicham', name: 'Medicham', dex: 308, shadowEligible: true },
        { id: 'swampert', name: 'Swampert', dex: 260, shadowEligible: true },
      ]),
    '/api/compare': () => json(COMPARED),
  })
}

function renderWith(ui: ReactNode) {
  return render(
    <MemoryRouter>
      <SpeciesProvider>
        <CollectionProvider>{ui}</CollectionProvider>
      </SpeciesProvider>
    </MemoryRouter>,
  )
}

const box = (list: CaughtPokemon[]) => localStorage.setItem('dexpresso.guestCollection.v1', JSON.stringify(list))

beforeEach(() => localStorage.clear())
afterEach(cleanup)

describe('Which One to Build placement', () => {
  // The main nav always links to it, with a count of the species you have two or more of
  it('is in the nav, counting your duplicate species', async () => {
    serve()
    box(BOX)
    renderWith(<Header />)
    await expect.poll(() => screen.queryByRole('link', { name: /Which to Build/ })?.getAttribute('href')).toBe('/build')
    await expect.poll(() => screen.getByRole('link', { name: /Which to Build/ }).textContent).toContain('1')
  })

  // Its page lists each species you have 2+ copies of, linking to that species' comparison for the League picked
  it('has a page listing each species to decide on', async () => {
    serve()
    box(BOX)
    const user = userEvent.setup()
    renderWith(<BuildPage />)
    const link = () => screen.queryByRole('link', { name: /Medicham/ })
    await expect.poll(() => link()?.getAttribute('href')).toBe('/species/medicham?league=great#compare')
    expect(link()!.textContent).toContain('2 copies')
    expect(screen.queryByRole('link', { name: /Swampert/ })).toBeNull()

    await user.click(screen.getByRole('tab', { name: 'Ultra' }))
    expect(link()!.getAttribute('href')).toBe('/species/medicham?league=ultra#compare')
  })

  // The Team Builder names your duplicates and links straight to the comparison for its League
  it('calls out your duplicates on the Team Builder', async () => {
    serve()
    box(BOX)
    renderWith(<BuildCallout league="ULTRA" />)
    const callout = () => screen.queryByRole('region', { name: /Which One to Build/ })
    await expect.poll(() => callout()?.textContent ?? '').toContain('You have 2 Medicham')
    expect(
      within(callout()!)
        .getByRole('link', { name: /Medicham/ })
        .getAttribute('href'),
    ).toBe('/species/medicham?league=ultra#compare')
  })

  // With no duplicates yet, the callout still explains the feature and links to its page
  it('still shows the callout with no duplicates', async () => {
    serve()
    box([copy(3, 'swampert', 1)])
    renderWith(<BuildCallout league="GREAT" />)
    const callout = () => screen.queryByRole('region', { name: /Which One to Build/ })
    await expect.poll(() => callout()).toBeTruthy()
    expect(within(callout()!).getByRole('link').getAttribute('href')).toBe('/build')
  })
})

describe('Build This One card', () => {
  // The top two copies side by side, each with its meta win rate; the better one gets the bold badge with its edge
  it('puts the top two side by side with a win-rate badge', async () => {
    serve()
    box(BOX)
    renderWith(<CompareCopies speciesId="medicham" leagueKey="great" leagueLabel="Great League" rankOf={(p) => p.id} />)
    const card = () => screen.queryByRole('group', { name: /Build This One/ })
    await expect.poll(() => card()).toBeTruthy()
    const sides = within(card()!).getAllByRole('article')
    expect(sides.map((s) => s.getAttribute('aria-label'))).toEqual(['4/14/15', '0/15/15'])
    expect(sides[0].textContent).toContain('66.7%')
    expect(sides[1].textContent).toContain('54.4%')
    expect(sides[0].textContent).toContain('Build This One: +12.2% Meta Win Rate')
    expect(sides[1].textContent).not.toContain('Build This One')
  })
})

describe('Match review nudge', () => {
  // A review of a battle with a Pokémon you have 2 copies of says how many more meta matchups the better one wins
  it('nudges toward the comparison for a Pokémon you have two of', async () => {
    serve()
    box(BOX)
    const result = sampleResult()
    result.turn_log.user_team = ['Medicham', 'Swampert', 'Cradily']
    result.turn_log.cp = { 'user:Medicham': 1498, 'user:Swampert': 1497, 'user:Cradily': 1499 }
    renderWith(<BuildNudge result={result} />)
    const nudge = () => screen.queryByRole('region', { name: /Which One to Build/ })
    await expect
      .poll(() => nudge()?.textContent ?? '')
      .toContain('You have 2 Medicham in your box: see which one wins 11 more meta matchups.')
    expect(within(nudge()!).getByRole('link').getAttribute('href')).toBe('/species/medicham?league=great#compare')
  })

  // No duplicates among the Pokémon you battled with: no nudge
  it('stays away when you have one of each', async () => {
    const fetchMock = serve()
    box([copy(1, 'medicham', 0), copy(3, 'swampert', 1)])
    const result = sampleResult()
    result.turn_log.user_team = ['Medicham', 'Swampert', 'Cradily']
    result.turn_log.cp = { 'user:Medicham': 1498, 'user:Swampert': 1497, 'user:Cradily': 1499 }
    renderWith(<BuildNudge result={result} />)
    await expect.poll(() => fetchMock.mock.calls.some(([url]) => url === '/api/species')).toBe(true)
    await new Promise((r) => setTimeout(r, 50))
    expect(screen.queryByRole('region', { name: /Which One to Build/ })).toBeNull()
    expect(fetchMock.mock.calls.some(([url]) => url === '/api/compare')).toBe(false)
  })
})

// @vitest-environment jsdom
// Issue #113: each of the Team Builder's 5 teams shows its meta coverage and the threats it struggles with.
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { TeamBuilderPage } from './TeamBuilderPage'
import { fakeServer, json } from '../analysis/test-helpers'
import type { TeamMember, TeamResult } from '../types'
import { CollectionProvider } from '../collection/CollectionProvider'
import { suggestion } from '../components/suggestion-fixture'

function member(index: number, speciesId: string): TeamMember {
  return {
    index,
    speciesId,
    shadow: false,
    level: 40,
    cp: 1490,
    atkIv: 1,
    defIv: 15,
    staIv: 15,
    fastMove: 'MUD_SHOT',
    chargedMoves: ['HYDRO_CANNON'],
    metaRating: 520,
    assumed: true,
    powerUp: null,
    cost: null,
  }
}

/** Team `t` of 5: winning 60% down to 52%, answering 35 down to 31 of the 44 meta Pokémon */
function team(t: number): TeamResult {
  return {
    members: [member(0, 'swampert'), member(1, 'cradily'), member(2 + t, `third${t}`)],
    winRate: { rate: 0.6 - t * 0.02, low: 0.55 - t * 0.02, high: 0.65 - t * 0.02, battles: 400 },
    toughest: ['clodsire', 'kingdra', 'altaria'],
    suggestions: [],
    coverage: { rating: 563.24 - t, answered: 35 - t, metaSize: 44 },
    threats: [
      { opponent: 'clodsire', rating: 312.46, answer: 'swampert' },
      { opponent: 'kingdra', rating: 380, answer: 'cradily' },
      { opponent: 'altaria', rating: 401.2, answer: 'cradily' },
      { opponent: 'annihilape_shadow', rating: 455, answer: 'swampert' },
      { opponent: 'furret', rating: 470.5, answer: 'cradily' },
    ],
  }
}

const teams = [0, 1, 2, 3, 4].map(team)

function renderBuilder() {
  const battleTeam = {
    names: ['Swampert', 'Cradily', 'Clodsire'],
    league: 'GREAT',
    species: ['swampert', 'cradily', 'clodsire'].map((speciesId) => ({ speciesId, shadow: false })),
  }
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/team-builder', state: { battleTeam } }]}>
      <Routes>
        <Route path="/team-builder" element={<TeamBuilderPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

async function build() {
  fakeServer({
    '/api/team-builder': () =>
      json({ league: 'GREAT', metaSize: 44, metaDate: '2026-11-09', eligible: 7, skipped: [], cup: null, teams }),
  })
  renderBuilder()
  await userEvent.setup().click(screen.getByRole('button', { name: 'Build teams' }))
  await screen.findByRole('heading', { name: /Best Teams for/ })
}

const cards = () =>
  screen.getAllByRole('listitem').filter((li) => /Team \d/.test(li.querySelector('.eyebrow')?.textContent ?? ''))

beforeEach(() => {
  localStorage.clear()
  // jsdom has no matchMedia; the odds rings ask it whether to animate (reduced motion: no)
  window.matchMedia = ((query: string) => ({
    matches: true,
    media: query,
    addEventListener() {},
    removeEventListener() {},
  })) as unknown as typeof window.matchMedia
})
afterEach(cleanup)

describe('Team Builder results', () => {
  // Each card says how much of the meta the team covers: how many meta Pokémon it beats, and its coverage rating
  it('shows each team’s meta coverage', async () => {
    await build()
    expect(cards()[0].textContent).toContain('Beats 35 of 44 meta Pokémon')
    expect(cards()[0].textContent).toContain('coverage 563')
    expect(cards()[4].textContent).toContain('Beats 31 of 44 meta Pokémon')
  })

  // Each card lists its 5 worst matchups, worst first, with the best rating the team gets and who gets it
  it('lists each team’s threats with ratings', async () => {
    await build()
    const lists = within(cards()[0]).queryAllByRole('list', { name: 'Toughest matchups' })
    expect(lists).toHaveLength(1)
    const items = within(lists[0]).getAllByRole('listitem')
    expect(items).toHaveLength(5)
    expect(items[0].textContent).toContain('clodsire')
    expect(items[0].textContent).toContain('312')
    expect(items[0].textContent).toContain('swampert')
    expect(items[3].textContent).toContain('Shadow annihilape')
  })
})

// ---- #114: "Add to Box & Re-Analyze" on a suggestion ----

const MIMIKYU = suggestion('mimikyu', 'CLOSER', {
  replaces: 2,
  replacesSpeciesId: 'clodsire',
  level: 25,
  atkIv: 4,
  defIv: 14,
  staIv: 15,
  fastMove: 'SHADOW_CLAW',
  chargedMoves: ['SHADOW_SNEAK', 'PLAY_ROUGH'],
})
const MIMIKYU_BUILD = {
  speciesId: 'mimikyu',
  atkIv: 4,
  defIv: 14,
  staIv: 15,
  level: 25,
  fastMove: 'SHADOW_CLAW',
  chargedMoves: ['SHADOW_SNEAK', 'PLAY_ROUGH'],
  shadow: false,
}

/** The Team Builder: a 42% team with Mimikyu suggested, then (once Mimikyu is in) a 61% team */
function teamBuilderAnswers() {
  let calls = 0
  return () => {
    calls++
    const first = calls === 1
    const result = { ...team(0), winRate: { rate: first ? 0.42 : 0.61, low: 0.4, high: 0.65, battles: 400 } }
    return json({
      league: 'GREAT',
      metaSize: 44,
      metaDate: '2026-11-09',
      eligible: first ? 3 : 4,
      skipped: [],
      cup: null,
      teams: [{ ...result, suggestions: first ? [MIMIKYU] : [] }],
    })
  }
}

function renderWithCollection(state?: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/team-builder', state }]}>
      <CollectionProvider>
        <Routes>
          <Route path="/team-builder" element={<TeamBuilderPage />} />
        </Routes>
      </CollectionProvider>
    </MemoryRouter>,
  )
}

const requestsTo = (fetchMock: ReturnType<typeof fakeServer>, url: string) =>
  fetchMock.mock.calls.filter(([u]) => u === url).map(([, init]) => JSON.parse(String(init?.body)))

const BATTLE_TEAM = {
  battleTeam: {
    names: ['Swampert', 'Cradily', 'Clodsire'],
    league: 'GREAT',
    species: ['swampert', 'cradily', 'clodsire'].map((speciesId) => ({ speciesId, shadow: false })),
  },
}

describe('Add to Box & Re-Analyze', () => {
  // A guest: the suggestion goes into this browser's box with its meta build, and the teams are rebuilt with it in,
  // straight away, showing the new score
  it('adds to a guest’s box and rebuilds', async () => {
    const fetchMock = fakeServer({
      '/api/collection': () => json({ detail: 'log in' }, 401),
      '/api/checklist': () => json({ detail: 'log in' }, 401),
      '/api/team-builder': teamBuilderAnswers(),
    })
    const user = userEvent.setup()
    renderWithCollection(BATTLE_TEAM)
    await user.click(await screen.findByRole('button', { name: 'Build teams' }))
    await user.click(await screen.findByRole('button', { name: 'Add mimikyu to Box & Re-Analyze' }))

    await expect.poll(() => requestsTo(fetchMock, '/api/team-builder').length).toBe(2)
    const rebuilt = requestsTo(fetchMock, '/api/team-builder')[1]
    expect(rebuilt.pokemon).toEqual([MIMIKYU_BUILD])
    expect(rebuilt.species.map((s: { speciesId: string }) => s.speciesId)).toEqual(['swampert', 'cradily', 'clodsire'])
    const box = JSON.parse(localStorage.getItem('dexpresso.guestCollection.v1') ?? '[]')
    expect(box.map((p: { speciesId: string }) => p.speciesId)).toEqual(['mimikyu'])
    await expect.poll(() => cards()[0]?.textContent ?? '').toContain('61%')
  })

  // Logged in: the account's box gets it through the meta-preset endpoint (the server works out the build), then
  // the teams are rebuilt from the Collection with it in
  it('adds to the account’s box and rebuilds', async () => {
    const saved = { id: 7, ...MIMIKYU_BUILD }
    const fetchMock = fakeServer({
      '/api/collection': () => json([]),
      '/api/checklist': () =>
        json(['swampert', 'cradily', 'clodsire'].map((speciesId) => ({ speciesId, shadow: false }))),
      '/api/collection/meta-preset': () => json(saved, 201),
      '/api/team-builder': teamBuilderAnswers(),
    })
    const user = userEvent.setup()
    renderWithCollection()
    await user.click(await screen.findByRole('button', { name: 'Build teams' }))
    await user.click(await screen.findByRole('button', { name: 'Add mimikyu to Box & Re-Analyze' }))

    await expect.poll(() => requestsTo(fetchMock, '/api/team-builder').length).toBe(2)
    expect(requestsTo(fetchMock, '/api/collection/meta-preset')).toEqual([
      { speciesId: 'mimikyu', shadow: false, league: 'GREAT', cup: null },
    ])
    expect(requestsTo(fetchMock, '/api/team-builder')[1].pokemon).toEqual([MIMIKYU_BUILD])
    await expect.poll(() => cards()[0]?.textContent ?? '').toContain('61%')
  })
})

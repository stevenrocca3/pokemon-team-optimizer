// @vitest-environment jsdom
// Issue #113: each of the Team Builder's 5 teams shows its meta coverage and the threats it struggles with.
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { TeamBuilderPage } from './TeamBuilderPage'
import { fakeServer, json } from '../analysis/test-helpers'
import type { TeamMember, TeamResult } from '../types'

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

// @vitest-environment jsdom
// Issue #140: a Collection card's evolution line: stage toggles, each stage's League builds, and Simulate in Team Builder
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { EvolutionLine } from './EvolutionLine'
import { fakeServer, json } from '../analysis/test-helpers'
import type { CaughtPokemon, EvolutionProjection, LeagueProjection } from '../types'

const MUDKIP: CaughtPokemon = { id: 4, speciesId: 'mudkip', atkIv: 0, defIv: 15, staIv: 15, level: 20, fastMove: 'TACKLE', chargedMoves: ['DIG'], shadow: false }

const league = (l: LeagueProjection['league'], over: Partial<LeagueProjection>): LeagueProjection => ({
  league: l, eligible: true, level: null, cp: null, statProduct: null, statProductRank: null, percentOfBest: null,
  needsXlCandy: false, bestBuddyCp: null, ...over,
})

const PROJECTION: EvolutionProjection = {
  speciesId: 'mudkip',
  name: 'Mudkip',
  shadow: false,
  forms: [
    {
      speciesId: 'marshtomp', name: 'Marshtomp', dex: 259, stage: 1, path: ['mudkip', 'marshtomp'],
      leagues: [
        league('GREAT', { level: 34.5, cp: 1493, percentOfBest: 98.72 }),
        league('ULTRA', { level: 50, cp: 2014, percentOfBest: 91.3, needsXlCandy: true }),
        league('MASTER', { level: 50, cp: 2014, percentOfBest: 80.1, needsXlCandy: true, bestBuddyCp: 2050 }),
      ],
      moves: { optimalFastMove: 'MUD_SHOT', optimalChargedMoves: ['SURF', 'SLUDGE'], warnings: [] },
    },
    {
      speciesId: 'swampert', name: 'Swampert', dex: 260, stage: 2, path: ['mudkip', 'marshtomp', 'swampert'],
      leagues: [
        league('GREAT', { eligible: false }),
        league('ULTRA', { level: 30, cp: 2488, percentOfBest: 99.12 }),
        league('MASTER', { level: 50, cp: 3200, percentOfBest: 95, needsXlCandy: true, bestBuddyCp: 3260 }),
      ],
      moves: {
        optimalFastMove: 'MUD_SHOT',
        optimalChargedMoves: ['HYDRO_CANNON', 'EARTHQUAKE'],
        warnings: [{ moveId: 'HYDRO_CANNON', name: 'Hydro Cannon', fast: false, message: 'Swampert needs Hydro Cannon: an Elite Charged TM.' }],
      },
    },
  ],
}

function Probe() {
  return <pre data-testid="state">{JSON.stringify(useLocation().state)}</pre>
}

function renderLine() {
  const fetchMock = fakeServer({ '/api/evolutions': () => json(PROJECTION) })
  render(
    <MemoryRouter initialEntries={['/collection']}>
      <Routes>
        <Route path="/collection" element={<EvolutionLine pokemon={MUDKIP} name="Mudkip" />} />
        <Route path="/team-builder" element={<Probe />} />
      </Routes>
    </MemoryRouter>,
  )
  return fetchMock
}

async function open() {
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: /Evolutions/ }))
  await screen.findByRole('tablist', { name: 'Evolution stages' })
  return user
}

afterEach(cleanup)

describe('EvolutionLine', () => {
  it('asks for the projection only once opened, with this Pokémon’s IVs and level', async () => {
    const fetchMock = renderLine()
    expect(fetchMock).not.toHaveBeenCalled()
    await open()
    const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/evolutions')!
    expect(JSON.parse(String(init?.body))).toEqual({ speciesId: 'mudkip', atkIv: 0, defIv: 15, staIv: 15, level: 20, shadow: false })
  })

  it('shows a toggle per stage, starting on the next one, with its League builds', async () => {
    renderLine()
    await open()
    const tabs = within(screen.getByRole('tablist', { name: 'Evolution stages' })).getAllByRole('tab')
    expect(tabs.map((t) => t.textContent)).toEqual(['Marshtomp', 'Swampert'])
    expect(tabs[0].getAttribute('aria-selected')).toBe('true')

    const great = screen.getByRole('listitem', { name: 'Great League' })
    expect(great.textContent).toContain('1,493 CP')
    expect(great.textContent).toContain('Lv 34.5')
    expect(great.textContent).toContain('98.7%')
    expect(screen.getByRole('listitem', { name: 'Ultra League' }).textContent).toContain('XL')
  })

  // Swampert is already over 1,500 CP at level 20, so Great League is out; Ultra and Master aren't
  it('switches stage, marking Leagues it is too strong for and showing move warnings', async () => {
    renderLine()
    const user = await open()
    await user.click(screen.getByRole('tab', { name: 'Swampert' }))

    const great = screen.getByRole('listitem', { name: 'Great League' })
    expect(great.textContent).toContain('Too strong')
    expect(within(great).queryByRole('button')).toBeNull()
    expect(screen.getByRole('listitem', { name: 'Ultra League' }).textContent).toContain('2,488 CP')
    expect(screen.getByRole('listitem', { name: 'Master League' }).textContent).toContain('Best Buddy 3,260')
    expect(screen.getByText(/Swampert needs Hydro Cannon/)).toBeTruthy()
  })

  it('sends the evolved form to the Team Builder in that League', async () => {
    renderLine()
    const user = await open()
    await user.click(screen.getByRole('tab', { name: 'Swampert' }))
    await user.click(screen.getByRole('button', { name: 'Simulate Swampert in Ultra League' }))

    const state = JSON.parse((await screen.findByTestId('state')).textContent ?? 'null')
    expect(state.simulate).toEqual({
      league: 'ULTRA',
      from: { speciesId: 'mudkip', atkIv: 0, defIv: 15, staIv: 15, level: 20, fastMove: 'TACKLE', chargedMoves: ['DIG'], shadow: false },
      as: { speciesId: 'swampert', atkIv: 0, defIv: 15, staIv: 15, level: 20, fastMove: 'MUD_SHOT', chargedMoves: ['HYDRO_CANNON', 'EARTHQUAKE'], shadow: false },
      fromName: 'Mudkip',
      asName: 'Swampert',
    })
  })
})

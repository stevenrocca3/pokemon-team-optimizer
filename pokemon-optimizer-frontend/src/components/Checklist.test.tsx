// @vitest-environment jsdom
// Issue #116: the checklist's quick-select grid is PvPoke's top 150 for Great and Ultra League, and its top 50 for Master.
// Issue #117: a Cups tab beside the Leagues shows each live Cup's meta in the same grid.
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { Checklist } from './Checklist'
import { CollectionProvider } from '../collection/CollectionProvider'
import { fakeServer, json } from '../analysis/test-helpers'
import type { CupSummary, MetaPick } from '../types'

/** `count` ranked picks: "great1", "great2"…, every third a Shadow, Water/Fairy and Steel alternating */
function picks(league: string, count: number): MetaPick[] {
  return Array.from({ length: count }, (_, i) => ({
    speciesId: `${league}${i + 1}`,
    shadow: i % 3 === 2,
    types: i % 2 === 0 ? ['water', 'fairy'] : ['steel'],
  }))
}

function cup(key: string, title: string, league: CupSummary['league'], metaSize: number): CupSummary {
  return { key, title, league, partySize: 3, rules: [], metaSize, updated: '2026-11-08' }
}

function serve(cups: CupSummary[] = []) {
  return fakeServer({
    '/api/cups': () => json(cups),
    '/api/meta/cup/fantasy-1500': () => json(picks('fantasy', 26)),
    '/api/meta/cup/mega-10000': () => json(picks('megamaster', 37)),
    '/api/collection': () => json({ detail: 'log in' }, 401),
    '/api/checklist': () => json({ detail: 'log in' }, 401),
    '/api/meta/GREAT/top': () => json(picks('great', 150)),
    '/api/meta/ULTRA/top': () => json(picks('ultra', 150)),
    '/api/meta/MASTER/top': () => json(picks('master', 50)),
  })
}

function renderChecklist() {
  return render(
    <MemoryRouter>
      <CollectionProvider>
        <Checklist />
      </CollectionProvider>
    </MemoryRouter>,
  )
}

const grid = () => screen.queryByRole('list', { name: /quick select/i })
const cards = () => (grid() ? within(grid()!).getAllByRole('listitem') : [])

beforeEach(() => localStorage.clear())
afterEach(cleanup)

describe('Quick-select grid', () => {
  // Great League opens with all 150 of its top Pokémon, best first; Master League has its top 50
  it('shows the top 150, or 50 for Master League', async () => {
    serve()
    const user = userEvent.setup()
    renderChecklist()
    await expect.poll(() => cards().length).toBe(150)
    expect(cards()[0].textContent).toContain('great1')
    expect(cards()[149].textContent).toContain('great150')

    await user.click(screen.getByRole('tab', { name: 'Master' }))
    await expect.poll(() => cards()[0]?.textContent ?? '').toContain('master1')
    expect(cards()).toHaveLength(50)
  })

  // A card shows its rank, its type badges and whether it's a Shadow
  it('shows rank, types and Shadow on each card', async () => {
    serve()
    renderChecklist()
    await expect.poll(() => cards().length).toBe(150)
    const third = cards()[2]
    expect(third.textContent).toContain('#3')
    expect(third.textContent).toContain('Shadow')
    expect([...third.querySelectorAll('[data-type]')].map((b) => b.getAttribute('data-type'))).toEqual([
      'water',
      'fairy',
    ])
    expect([...cards()[1].querySelectorAll('[data-type]')].map((b) => b.textContent)).toEqual(['Steel'])
  })

  // Its toggle ticks it in one tap (a guest's ticks stay in this browser), and tapping again unticks it
  it('quick-add toggles tick and untick', async () => {
    serve()
    const user = userEvent.setup()
    renderChecklist()
    await expect.poll(() => cards().length).toBe(150)
    const toggle = within(cards()[2]).getByRole('checkbox', { name: /great3/ })

    await user.click(toggle)
    expect(JSON.parse(localStorage.getItem('dexpresso.guestChecklist.v1') ?? '[]')).toEqual([
      { speciesId: 'great3', shadow: true },
    ])
    expect(screen.getByText(/1 of 150 ticked/)).toBeTruthy()
    await user.click(toggle)
    expect(localStorage.getItem('dexpresso.guestChecklist.v1')).toBeNull()
  })
})

describe('Cups tab', () => {
  const live = [cup('fantasy-1500', 'Fantasy Cup', 'GREAT', 26), cup('mega-10000', 'Mega Master League', 'MASTER', 37)]

  // Beside Great, Ultra and Master is a Cups tab; it lists the live Cups and opens on the first one's meta, with types
  it("lists the live Cups and shows the first one's meta", async () => {
    serve(live)
    const user = userEvent.setup()
    renderChecklist()
    await expect.poll(() => cards().length).toBe(150)
    await expect
      .poll(() => screen.queryAllByRole('tab').map((t) => t.textContent))
      .toEqual(['Great', 'Ultra', 'Master', 'Cups'])

    await user.click(screen.getByRole('tab', { name: 'Cups' }))
    await expect.poll(() => grid()?.getAttribute('aria-label')).toBe('Fantasy Cup quick select')
    expect(cards()).toHaveLength(26)
    expect(cards()[0].textContent).toContain('fantasy1')
    expect([...cards()[0].querySelectorAll('[data-type]')].map((b) => b.getAttribute('data-type'))).toEqual([
      'water',
      'fairy',
    ])
    expect(screen.getByRole('button', { name: /Fantasy Cup/, pressed: true })).toBeTruthy()
    expect(screen.getByRole('button', { name: /Mega Master League/, pressed: false })).toBeTruthy()
  })

  // Picking another Cup swaps the grid to its meta; going back to a League tab brings that League's top list back
  it('switches between Cups and back to a League', async () => {
    serve(live)
    const user = userEvent.setup()
    renderChecklist()
    await expect.poll(() => cards().length).toBe(150)
    await expect.poll(() => screen.queryByRole('tab', { name: 'Cups' })).toBeTruthy()
    await user.click(screen.getByRole('tab', { name: 'Cups' }))
    await expect.poll(() => cards().length).toBe(26)

    await user.click(screen.getByRole('button', { name: /Mega Master League/ }))
    await expect.poll(() => grid()?.getAttribute('aria-label')).toBe('Mega Master League quick select')
    expect(cards()).toHaveLength(37)
    expect(cards()[0].textContent).toContain('megamaster1')

    await user.click(screen.getByRole('tab', { name: 'Ultra' }))
    await expect.poll(() => grid()?.getAttribute('aria-label')).toBe('Ultra League quick select')
    expect(cards()[0].textContent).toContain('ultra1')
  })

  // With no Cups running, the tab says so instead of showing an empty grid
  it('says when no Cups are running', async () => {
    serve([])
    const user = userEvent.setup()
    renderChecklist()
    await expect.poll(() => cards().length).toBe(150)
    await expect.poll(() => screen.queryByRole('tab', { name: 'Cups' })).toBeTruthy()

    await user.click(screen.getByRole('tab', { name: 'Cups' }))
    await expect.poll(() => screen.queryByText(/No Cups are running right now/)).toBeTruthy()
    expect(grid()).toBeNull()
  })
})

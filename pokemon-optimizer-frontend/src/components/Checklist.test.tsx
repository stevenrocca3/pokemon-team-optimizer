// @vitest-environment jsdom
// Issue #116: the checklist's quick-select grid is PvPoke's top 150 for Great and Ultra League, and its top 50 for Master.
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { Checklist } from './Checklist'
import { CollectionProvider } from '../collection/CollectionProvider'
import { fakeServer, json } from '../analysis/test-helpers'
import type { MetaPick } from '../types'

/** `count` ranked picks: "great1", "great2"…, every third a Shadow, Water/Fairy and Steel alternating */
function picks(league: string, count: number): MetaPick[] {
  return Array.from({ length: count }, (_, i) => ({
    speciesId: `${league}${i + 1}`,
    shadow: i % 3 === 2,
    types: i % 2 === 0 ? ['water', 'fairy'] : ['steel'],
  }))
}

function serve() {
  return fakeServer({
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

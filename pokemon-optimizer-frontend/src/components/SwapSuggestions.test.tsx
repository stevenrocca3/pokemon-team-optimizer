// @vitest-environment jsdom
// Issue #112: a team winning under half its battles shows 5 swaps from the meta, each with its GBL role.
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { SwapSuggestions } from './SwapSuggestions'
import type { Suggestion, TeamMember } from '../types'
import { suggestion } from './suggestion-fixture'

afterEach(cleanup)

const member = (index: number, speciesId: string) => ({ index, speciesId }) as TeamMember
const members = [member(0, 'swampert'), member(1, 'cradily'), member(2, 'clodsire')]

const five = [
  suggestion('medicham', 'LEAD'),
  suggestion('registeel', 'SAFE_SWAP', { replaces: 1, replacesSpeciesId: 'cradily', coverageGain: 20 }),
  suggestion('furret', 'CLOSER', { shadow: true, coverageGain: 12.3 }),
  suggestion('altaria', 'LEAD', { coverageGain: 8 }),
  suggestion('carbink', 'SAFE_SWAP', { coverageGain: 2 }),
]

function renderSwaps(list: Suggestion[]) {
  return render(
    <MemoryRouter>
      <SwapSuggestions suggestions={list} members={members} />
    </MemoryRouter>,
  )
}

describe('SwapSuggestions', () => {
  // All 5 come out in order, each labelled with its role: Lead, Safe Swap or Switcher/Closer
  it('lists the five swaps with their roles', () => {
    renderSwaps(five)
    const items = within(screen.getByRole('list', { name: 'Swaps for this team' })).getAllByRole('listitem')
    expect(items).toHaveLength(5)
    expect(items.map((i) => i.querySelector('[data-role]')?.textContent)).toEqual([
      'Lead',
      'Safe Swap',
      'Switcher/Closer',
      'Lead',
      'Safe Swap',
    ])
  })

  // Each says who it replaces on the team, what it adds to meta coverage and the meta Pokémon it helps against
  it('says who each one replaces and what it fixes', () => {
    renderSwaps(five)
    const items = within(screen.getByRole('list', { name: 'Swaps for this team' })).getAllByRole('listitem')
    expect(items[1].textContent).toContain('for cradily')
    expect(items[1].textContent).toContain('+20.0 coverage')
    expect(items[0].textContent).toContain('Helps against azumarill, Shadow annihilape')
    expect(items[2].textContent).toContain('Shadow')
  })

  // A team at 50% or better has none, so nothing is shown
  it('shows nothing without suggestions', () => {
    const { container } = renderSwaps([])
    expect(container.innerHTML).toBe('')
  })
})

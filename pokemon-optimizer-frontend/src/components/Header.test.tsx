// @vitest-environment jsdom
// Phones hide the wide nav, so a Menu button has to reach every page, the battle analysis tool included.
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { SpeciesProvider } from '../species/SpeciesProvider'
import { CollectionProvider } from '../collection/CollectionProvider'
import { fakeServer, json } from '../analysis/test-helpers'
import { Header } from './Header'

function serve() {
  return fakeServer({
    '/api/collection': () => json({ detail: 'log in' }, 401),
    '/api/checklist': () => json({ detail: 'log in' }, 401),
    '/api/species': () => json([]),
  })
}

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>
}

function renderHeader() {
  return render(
    <MemoryRouter initialEntries={['/collection']}>
      <SpeciesProvider>
        <CollectionProvider>
          <Header />
          <Routes>
            <Route path="*" element={<Where />} />
          </Routes>
        </CollectionProvider>
      </SpeciesProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => localStorage.clear())
afterEach(cleanup)

describe('phone menu', () => {
  it('opens a list of every page, and going to Analyze closes it', async () => {
    serve()
    const user = userEvent.setup()
    renderHeader()

    const button = screen.getByRole('button', { name: 'Menu' })
    expect(button.getAttribute('aria-expanded')).toBe('false')
    await user.click(button)
    expect(button.getAttribute('aria-expanded')).toBe('true')

    const menu = screen.getByRole('navigation', { name: 'Menu' })
    const pages = within(menu)
      .getAllByRole('link')
      .map((a) => a.getAttribute('href'))
    expect(pages).toEqual(['/team-builder', '/collection', '/analyze', '/build', '/import'])

    await user.click(within(menu).getByRole('link', { name: /Analyze/ }))
    expect(screen.getByTestId('where').textContent).toBe('/analyze')
    expect(screen.queryByRole('navigation', { name: 'Menu' })).toBeNull()
  })

  // battle analysis is new: its link says so in the wide nav and in the phone menu
  it('marks Analyze as new in both navs', async () => {
    serve()
    const user = userEvent.setup()
    renderHeader()
    await user.click(screen.getByRole('button', { name: 'Menu' }))
    const links = screen.getAllByRole('link', { name: /Analyze/ })
    expect(links).toHaveLength(2)
    for (const link of links) expect(link.textContent).toMatch(/New/)
  })

  it('closes on Escape', async () => {
    serve()
    const user = userEvent.setup()
    renderHeader()
    await user.click(screen.getByRole('button', { name: 'Menu' }))
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('navigation', { name: 'Menu' })).toBeNull()
  })
})

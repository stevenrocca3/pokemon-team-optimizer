// @vitest-environment jsdom
// Issue #120: battles saved as a guest move to the account once the player logs in.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render } from '@testing-library/react'
import { CollectionContext, type CollectionState } from '../collection/collection-context'
import { GuestBattleClaim } from './GuestBattleClaim'
import { json } from './test-helpers'

const TOKEN = '7f0c2c1e-4b8a-4d5e-9a61-2f3b4c5d6e7f'

function ready(storage: 'account' | 'browser'): CollectionState {
  return {
    status: 'ready',
    storage,
    list: [],
    add: async () => true,
    addAll: async () => ({ added: 0, raised: 0, skipped: 0 }),
    addSuggestion: async () => {
      throw new Error('not used')
    },
    remove: async () => {},
    checklist: [],
    setChecklist: () => {},
    checklistSave: { saving: false, error: null },
  }
}

function renderWith(state: CollectionState) {
  return render(
    <CollectionContext.Provider value={state}>
      <GuestBattleClaim />
    </CollectionContext.Provider>,
  )
}

let fetchMock: ReturnType<typeof vi.fn>
beforeEach(() => {
  localStorage.clear()
  fetchMock = vi.fn(async () => json({ claimed: 2 }))
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(cleanup)

const claims = () => fetchMock.mock.calls.filter(([url]) => url === '/api/v1/battles/claim')

describe('GuestBattleClaim', () => {
  // Logged in, with a guest token in this browser: one POST /api/v1/battles/claim carrying the token, even if the
  // page re-renders.
  it('claims the guest battles once the player is logged in', async () => {
    localStorage.setItem('dexpresso.guestToken', TOKEN)
    const view = renderWith(ready('account'))
    view.rerender(
      <CollectionContext.Provider value={ready('account')}>
        <GuestBattleClaim />
      </CollectionContext.Provider>,
    )
    await vi.waitFor(() => expect(claims()).toHaveLength(1))
    const [, init] = claims()[0]
    expect(init?.method).toBe('POST')
    expect(new Headers(init?.headers).get('X-Guest-Token')).toBe(TOKEN)
  })

  // Logged out (the Collection lives in the browser), or still loading: nothing to claim into, no request.
  it('does nothing for a guest', async () => {
    localStorage.setItem('dexpresso.guestToken', TOKEN)
    renderWith(ready('browser'))
    renderWith({ status: 'loading' })
    await new Promise((r) => setTimeout(r, 20))
    expect(claims()).toHaveLength(0)
  })

  // Logged in but this browser never analysed a battle as a guest (no token): no request, and no token made.
  it('does nothing without a guest token', async () => {
    renderWith(ready('account'))
    await new Promise((r) => setTimeout(r, 20))
    expect(claims()).toHaveLength(0)
    expect(localStorage.getItem('dexpresso.guestToken')).toBeNull()
  })
})

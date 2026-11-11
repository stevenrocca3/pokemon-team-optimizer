// @vitest-environment jsdom
// Issue #120: the token a guest's battles are saved under.
import { beforeEach, describe, expect, it } from 'vitest'
import { existingGuestToken, guestToken } from './guest-token'

beforeEach(() => localStorage.clear())

describe('guest token', () => {
  // The first call makes a random UUID and keeps it; later calls (and reloads) get the same one.
  it('is made once and kept', () => {
    const token = guestToken()
    expect(token).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    expect(guestToken()).toBe(token)
    expect(localStorage.getItem('dexpresso.guestToken')).toBe(token)
  })

  // Checking for one never makes one: a browser that never analysed a battle has nothing to claim.
  it('is only read by existingGuestToken', () => {
    expect(existingGuestToken()).toBeNull()
    expect(localStorage.getItem('dexpresso.guestToken')).toBeNull()
    const token = guestToken()
    expect(existingGuestToken()).toBe(token)
  })
})

import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, NotLoggedInError } from './http'

const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/problem+json', ...headers } })

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('apiFetch', () => {
  it('returns the response when it is ok', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(200, [1, 2])))
    const response = await apiFetch('/api/moves')
    expect(await response.json()).toEqual([1, 2])
  })

  it("uses the server's own explanation when it sends one", async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(400, { detail: 'Level: 40.3 must be a whole or half #.' })))
    await expect(apiFetch('/api/collection')).rejects.toThrow('Level: 40.3 must be a whole or half #.')
  })

  it('says how long to wait when rate limited', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 429, headers: { 'Retry-After': '42' } })))
    await expect(apiFetch('/api/feedback')).rejects.toThrow("You're going a bit fast. Try again in 42 seconds.")
  })

  it('never shows a bare status code for a server error without details', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>Bad Gateway</html>', { status: 502 })))
    await expect(apiFetch('/api/species')).rejects.toThrow(
      "DexPresso isn't answering right now. Try again in a minute.",
    )
  })

  it('explains being offline instead of "Failed to fetch"', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(apiFetch('/api/species')).rejects.toThrow("Can't reach DexPresso. Check your connection and try again.")
  })

  it('gives up on a request that hangs, with a clear message', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', vi.fn((_url: string, init: RequestInit) => new Promise((_resolve, reject) => {
      init.signal?.addEventListener('abort', () => reject(init.signal?.reason))
    })))
    const pending = expect(apiFetch('/api/species', { timeoutMs: 5_000 })).rejects.toThrow(
      'DexPresso took too long to answer. Try again in a moment.',
    )
    await vi.advanceTimersByTimeAsync(5_000)
    await pending
  })

  it("passes the caller's own cancel through untouched, so pages can ignore it", async () => {
    vi.stubGlobal('fetch', vi.fn((_url: string, init: RequestInit) => new Promise((_resolve, reject) => {
      init.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
    })))
    const cancel = new AbortController()
    const pending = apiFetch('/api/team-builder', { signal: cancel.signal })
    cancel.abort()
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('reports a missing login as its own error type', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 401 })))
    await expect(apiFetch('/api/collection')).rejects.toBeInstanceOf(NotLoggedInError)
  })
})

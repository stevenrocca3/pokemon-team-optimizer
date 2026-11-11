// @vitest-environment jsdom
// Issue #125: under 10 battles the rank hides behind a hazy, pixelated lock; the 10th battle reveals it, once.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { RankCard } from './AnalyticsDashboard'
import type { AnalyticsRank } from '../types'

const LOCKED: AnalyticsRank = { locked: true, battles: 6, required: 10, remaining: 4 }
const UNLOCKED: AnalyticsRank = {
  locked: false,
  battles: 10,
  required: 10,
  score: 74.3,
  tier: { key: 'ace', name: 'Ace / Mid-Ladder Execution' },
}
const REVEALED_KEY = 'dexpresso.rankRevealed'

function motion(reduced: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({ matches: reduced && query.includes('reduce'), media: query }))
}

const card = () => screen.getByRole('region', { name: 'Your rank' })

beforeEach(() => {
  localStorage.clear()
  motion(false)
})
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('RankCard', () => {
  // Locked: the notice "Submit more battle videos to receive your score" over the badge, with the progress counter
  // ("6 / 10 battles analyzed — 4 more required") and a progress bar at 6 of 10.
  it('locks the rank under 10 battles with progress', () => {
    render(<RankCard rank={LOCKED} />)
    expect(card().getAttribute('data-locked')).toBe('true')
    expect(screen.getByText('Submit more battle videos to receive your score')).toBeTruthy()
    expect(screen.getByText('6 / 10 battles analyzed — 4 more required')).toBeTruthy()
    const bar = screen.getByRole('progressbar', { name: 'Battles analyzed' })
    expect(bar.getAttribute('aria-valuenow')).toBe('6')
    expect(bar.getAttribute('aria-valuemax')).toBe('10')
  })

  // The badge under the lock is a pixel mosaic behind a frosted layer (blur(12px) contrast(150%)), and it's a
  // placeholder: no tier name or score is in the locked card at all.
  it('hides a placeholder badge behind the pixelated haze', () => {
    render(<RankCard rank={LOCKED} />)
    const mosaic = card().querySelector('[data-mosaic]')
    expect(mosaic).toBeTruthy()
    expect(mosaic!.querySelectorAll('rect').length).toBeGreaterThan(40)
    const frost = card().querySelector<HTMLElement>('[data-frost]')
    expect(frost?.className).toContain('backdrop-blur-[12px]')
    expect(frost?.className).toContain('backdrop-contrast-150')
    expect(card().textContent).not.toMatch(/Ace|Veteran|Legend|Critical|\d+\.\d/)
  })

  // The first time the rank is seen unlocked, it's revealed: the haze clears off the score (marked while it plays),
  // and this browser remembers it's been shown.
  it('reveals the rank the first time it’s unlocked', () => {
    render(<RankCard rank={UNLOCKED} />)
    expect(card().getAttribute('data-revealing')).toBe('true')
    expect(card().querySelector('[data-frost]')).toBeTruthy()
    expect(screen.getByText('Ace / Mid-Ladder Execution')).toBeTruthy()
    expect(localStorage.getItem(REVEALED_KEY)).toBe('1')
  })

  // After that it just shows: no lock, no reveal, the score and tier.
  it('shows an already revealed rank straight away', () => {
    localStorage.setItem(REVEALED_KEY, '1')
    render(<RankCard rank={UNLOCKED} />)
    expect(card().getAttribute('data-revealing')).toBeNull()
    expect(card().querySelector('[data-frost]')).toBeNull()
    expect(screen.getByText('74.3')).toBeTruthy()
    expect(screen.getByText('Ace / Mid-Ladder Execution')).toBeTruthy()
  })

  // With reduced motion there's no reveal animation: the score shows at once (and counts as revealed).
  it('skips the reveal with reduced motion', () => {
    motion(true)
    render(<RankCard rank={UNLOCKED} />)
    expect(card().getAttribute('data-revealing')).toBeNull()
    expect(screen.getByText('74.3')).toBeTruthy()
    expect(localStorage.getItem(REVEALED_KEY)).toBe('1')
  })
})

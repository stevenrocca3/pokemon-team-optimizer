// Issue #106: the timeline's geometry (the component draws what these return).
import { describe, expect, it } from 'vitest'
import { entry, sampleResult } from './match-fixture'
import { formatClock, share, timelineSpan, trackPath } from './timeline'

describe('timeline clock', () => {
  // The axis reads as a match clock from 00:00: 65 s is 01:05, 207.5 s is 03:27 (rounded down, like a stopwatch).
  it('formats milliseconds as mm:ss', () => {
    expect(formatClock(0)).toBe('00:00')
    expect(formatClock(65_000)).toBe('01:05')
    expect(formatClock(207_500)).toBe('03:27')
  })

  // The timeline spans the battle, not the video: from the first turn with the battle HUD up to the end of the last
  // (a recording that opens on 3 s of lobby starts its clock there). A moment's place is its share of that span.
  it('spans the battle and places moments by time', () => {
    const result = sampleResult()
    expect(timelineSpan(result.turn_log)).toEqual({ start: 0, end: 20_000 })
    const lobby = result.turn_log.turn_log.map((e) => (e.turn < 6 ? { ...e, hud_visible: false } : e))
    expect(timelineSpan({ ...result.turn_log, turn_log: lobby })).toEqual({ start: 3_000, end: 20_000 })
    expect(share(8_000, { start: 0, end: 20_000 })).toBe(0.4)
    expect(share(11_500, { start: 3_000, end: 20_000 })).toBe(0.5)
    expect(share(25_000, { start: 0, end: 20_000 })).toBe(1)
  })
})

describe('track paths', () => {
  // A track maps time across and the value (0-100) up: full HP at the top, empty at the bottom.
  it('scales time across and value up', () => {
    const entries = [entry(0, { user_hp_pct: 100 }), entry(1, { user_hp_pct: 50 }), entry(2, { user_hp_pct: 0 })]
    expect(trackPath(entries, (e) => e.user_hp_pct, { start: 0, end: 1000 }, 1000, 100)).toBe('M0,0 L500,50 L1000,100')
  })

  // Turns before the battle starts (the lobby) aren't drawn at all.
  it('leaves out turns outside the span', () => {
    const entries = [entry(0, { user_hp_pct: 10 }), entry(2, { user_hp_pct: 100 }), entry(4, { user_hp_pct: 0 })]
    expect(trackPath(entries, (e) => e.user_hp_pct, { start: 1000, end: 2000 }, 100, 100)).toBe('M0,0 L100,100')
  })

  // A turn with no reading (a cut-scene, a dropped frame) breaks the line rather than drawing through it.
  it('breaks the line where nothing was read', () => {
    const entries = [
      entry(0, { user_hp_pct: 100 }),
      entry(1, { user_hp_pct: null }),
      entry(2, { user_hp_pct: 40 }),
      entry(3, { user_hp_pct: 20 }),
    ]
    expect(trackPath(entries, (e) => e.user_hp_pct, { start: 0, end: 1500 }, 1500, 100)).toBe('M0,0 M1000,60 L1500,80')
  })
})

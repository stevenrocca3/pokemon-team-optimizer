import { describe, expect, it } from 'vitest'
import { DEMO_LOOP_MS, demoFrame, typed } from './demo-run'

describe('demoFrame', () => {
  it('starts by popping the Pokémon in one at a time', () => {
    expect(demoFrame(0)).toMatchObject({ phase: 'pick', picked: 1 })
    expect(demoFrame(1000)).toMatchObject({ phase: 'pick', picked: 5 })
  })

  it('never shows more Pokémon than the box has', () => {
    expect(demoFrame(2300).picked).toBe(8)
    expect(demoFrame(9000).picked).toBe(8)
  })

  it('picks the league, then taps Build', () => {
    expect(demoFrame(2600).phase).toBe('league')
    expect(demoFrame(3500).phase).toBe('tap')
  })

  it('ticks the four brewing steps off in order', () => {
    expect(demoFrame(4100)).toMatchObject({ phase: 'brew', stepsDone: 0 })
    expect(demoFrame(6500)).toMatchObject({ phase: 'brew', stepsDone: 2 })
    expect(demoFrame(8700)).toMatchObject({ phase: 'brew', stepsDone: 3 })
  })

  it('shows the result with how far into it we are', () => {
    const frame = demoFrame(8800 + 600)
    expect(frame.phase).toBe('result')
    expect(frame.resultMs).toBe(600)
  })

  it('loops back to the start', () => {
    expect(demoFrame(DEMO_LOOP_MS + 1000)).toEqual(demoFrame(1000))
  })
})

describe('typed', () => {
  it('types a caption out a few letters at a time', () => {
    expect(typed('Battling 1v1s', 0)).toBe('')
    expect(typed('Battling 1v1s', 100)).toBe('Batt')
    expect(typed('Battling 1v1s', 10_000)).toBe('Battling 1v1s')
  })
})

describe('demoFrame timing within a phase', () => {
  it('says how long the current phase has been showing, for its caption', () => {
    expect(demoFrame(2400 + 300)).toMatchObject({ phase: 'league', phaseMs: 300 })
  })
})

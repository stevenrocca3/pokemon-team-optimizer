import { describe, expect, it } from 'vitest'
import { groupByOpponent, shieldsList } from './matchups'

describe('groupByOpponent', () => {
  it('collects each opponent once with its shield scenarios, in order', () => {
    expect(
      groupByOpponent([
        { opponent: 'azumarill', shields: 0 },
        { opponent: 'carbink', shields: 2 },
        { opponent: 'azumarill', shields: 1 },
      ]),
    ).toEqual([
      { opponent: 'azumarill', shields: [0, 1] },
      { opponent: 'carbink', shields: [2] },
    ])
  })
})

describe('shieldsList', () => {
  it('reads naturally', () => {
    expect(shieldsList([0, 1, 2])).toBe('all shields')
    expect(shieldsList([2])).toBe('2 shields')
    expect(shieldsList([0])).toBe('0 shields')
    expect(shieldsList([0, 2])).toBe('0 and 2 shields')
  })
})

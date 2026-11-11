import { describe, expect, it } from 'vitest'
import type { TeamMember } from '../types'
import { buildTotals, shortfalls } from './build-cost'

/** A Mudkip built as Swampert: 25 + 100 Candy to evolve, a power-up, and a 10,000 / 25 second move */
function swampert(over: Partial<TeamMember> = {}): TeamMember {
  return {
    index: 0, speciesId: 'swampert', shadow: false, level: 41, cp: 1499, atkIv: 0, defIv: 15, staIv: 15,
    fastMove: 'MUD_SHOT', chargedMoves: ['HYDRO_CANNON', 'EARTHQUAKE'], metaRating: 520, assumed: false, powerUp: null,
    cost: { stardust: 200000, candy: 180, xlCandy: 20 },
    evolution: {
      fromSpeciesId: 'mudkip',
      moveWarnings: [],
      cost: {
        family: 'FAMILY_MUDKIP',
        steps: [
          { from: 'mudkip', into: 'marshtomp', candy: 25, item: null, itemCount: 0, lure: null, conditions: [] },
          { from: 'marshtomp', into: 'swampert', candy: 100, item: 'Sinnoh Stone', itemCount: 1, lure: 'Rainy Lure Module', conditions: [] },
        ],
        complete: true,
        evolveCandy: 125,
        secondMove: { stardust: 10000, candy: 25, xlCandy: 0 },
      },
    },
    ...over,
  }
}

describe('buildTotals', () => {
  it('adds evolving, powering up and the second move together', () => {
    expect(buildTotals(swampert())).toEqual({
      candy: 125 + 180 + 25,
      xlCandy: 20,
      stardust: 200000 + 10000,
      powerUpKnown: true,
      items: [{ name: 'Sinnoh Stone', count: 1 }],
      lures: ['Rainy Lure Module'],
    })
  })

  // added without a CP: its level isn't known, so only evolving and the second move are counted
  it('leaves the power-up out when the current level is unknown', () => {
    expect(buildTotals(swampert({ cost: null }))).toMatchObject({ candy: 150, xlCandy: 0, stardust: 10000, powerUpKnown: false })
  })
})

describe('shortfalls', () => {
  const totals = buildTotals(swampert())

  it('says how much of each you are short', () => {
    expect(shortfalls(totals, { candy: 300, xlCandy: 0, stardust: 500000 })).toEqual([
      'Insufficient Candy: short 30',
      'Insufficient Candy XL: short 20',
    ])
    expect(shortfalls(totals, { candy: 400, xlCandy: 5, stardust: 10000 })).toEqual([
      'Insufficient Candy XL: short 15',
      'Insufficient Stardust: short 200,000',
    ])
  })

  it('is empty when you have enough, and skips what you have not typed in', () => {
    expect(shortfalls(totals, { candy: 330, xlCandy: 20, stardust: 210000 })).toEqual([])
    expect(shortfalls(totals, { candy: null, xlCandy: null, stardust: 0 })).toEqual(['Insufficient Stardust: short 210,000'])
  })
})

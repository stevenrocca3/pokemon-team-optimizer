import { describe, expect, it } from 'vitest'
import { battleGrid, doseFrame, grindFrame, lineups, pullFrame, tasteFrame, wrapIndex } from './brew-steps'

describe('wrapIndex', () => {
  it('wraps past either end of the carousel', () => {
    expect(wrapIndex(-1, 4)).toBe(3)
    expect(wrapIndex(4, 4)).toBe(0)
    expect(wrapIndex(2, 4)).toBe(2)
  })
})

describe('grindFrame', () => {
  it('starts a 1v1 with both at full HP', () => {
    expect(grindFrame(0)).toMatchObject({ round: 0, foeHp: 100, youHp: 100, energy: 0 })
  })

  it('chips the foe with fast moves and builds energy', () => {
    const frame = grindFrame(1000)
    expect(frame.foeHp).toBeLessThan(100)
    expect(frame.energy).toBeGreaterThan(0)
  })

  it('knocks the foe out with a charged move', () => {
    expect(grindFrame(2400)).toMatchObject({ foeHp: 0, won: true })
  })

  it('loses some, too: every third 1v1 the foe wins', () => {
    expect(grindFrame(2 * 4000 + 2400)).toMatchObject({ youHp: 0, won: false })
  })

  it('moves on to a fresh 1v1', () => {
    expect(grindFrame(4100)).toMatchObject({ round: 1, foeHp: 100, won: null })
  })
})

describe('doseFrame', () => {
  it('sends the Pokémon up into their team folders', () => {
    expect(doseFrame(0).leafProgress).toBe(0)
    expect(doseFrame(1500).leafProgress).toBe(1)
  })

  it('stacks both teams into the root once they arrive', () => {
    expect(doseFrame(1500).stacked).toBe(0)
    expect(doseFrame(2900).stacked).toBe(2)
  })

  it('lights the root up only after the teams are stacked', () => {
    expect(doseFrame(1000).glow).toBe(false)
    expect(doseFrame(3200).glow).toBe(true)
  })
})

describe('lineups', () => {
  it('lists all six orders of three, starting with the one given', () => {
    const all = lineups(['a', 'b', 'c'])
    expect(all).toHaveLength(6)
    expect(all[0]).toEqual(['a', 'b', 'c'])
    expect(new Set(all.map((l) => l.join())).size).toBe(6)
    for (const l of all) expect([...l].sort()).toEqual(['a', 'b', 'c'])
  })
})

describe('pullFrame', () => {
  it('tries a new lineup every two seconds, all six, then again', () => {
    expect(pullFrame(100).lineup).toBe(0)
    expect(pullFrame(2100).lineup).toBe(1)
    expect(pullFrame(6 * 2000 + 100).lineup).toBe(0)
  })

  it('sends in the lead, then the safe swap, then the closer', () => {
    expect(pullFrame(500).active).toBe(0)
    expect(pullFrame(1000).active).toBe(1)
    expect(pullFrame(1400).active).toBe(2)
  })

  it('burns a shield early in the battle', () => {
    expect(pullFrame(500).shieldsLeft).toBe(2)
    expect(pullFrame(700).shieldsLeft).toBe(1)
  })

  it('counts through all 100 meta teams for each lineup', () => {
    expect(pullFrame(0).metaTeam).toBe(1)
    expect(pullFrame(1990).metaTeam).toBe(100)
  })
})

describe('battleGrid', () => {
  it('has exactly the wins asked for, mixed through the grid', () => {
    const grid = battleGrid(400, 214)
    expect(grid).toHaveLength(400)
    expect(grid.filter(Boolean)).toHaveLength(214)
    const first = grid.slice(0, 40)
    expect(first.some(Boolean)).toBe(true)
    expect(first.some((w) => !w)).toBe(true)
  })
})

describe('tasteFrame', () => {
  it('plays the 400 battles out one by one', () => {
    expect(tasteFrame(0).revealed).toBe(0)
    expect(tasteFrame(1000).revealed).toBe(142)
    expect(tasteFrame(2900).revealed).toBe(400)
  })

  it('ends on 214 wins of 400, the real run', () => {
    expect(tasteFrame(2900).wins).toBe(214)
  })

  it('shows the 95% range once every battle is in', () => {
    expect(tasteFrame(2000).showRange).toBe(false)
    expect(tasteFrame(3100).showRange).toBe(true)
  })
})

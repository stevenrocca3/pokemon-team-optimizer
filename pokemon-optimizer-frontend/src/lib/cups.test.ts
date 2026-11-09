import { describe, expect, it } from 'vitest'
import { cupsFor, formatName } from './cups'
import type { CupSummary } from '../types'

const cup = (key: string, title: string, league: CupSummary['league']): CupSummary => ({
  key, title, league, partySize: 3, rules: [], metaSize: 30, updated: '2026-11-03',
})

const FANTASY = cup('fantasy-1500', 'Fantasy Cup', 'GREAT')
const CAULDRON = cup('cauldron-2500', 'Battle Frontier (Cauldron)', 'ULTRA')
const DEVON = cup('fable-1500', 'Devon Fable Cup', 'GREAT')

describe('cupsFor', () => {
  it("lists only the League's Cups, by name", () => {
    expect(cupsFor([FANTASY, CAULDRON, DEVON], 'GREAT')).toEqual([DEVON, FANTASY])
  })

  it('is empty when the League has no Cups running', () => {
    expect(cupsFor([FANTASY], 'MASTER')).toEqual([])
  })
})

describe('formatName', () => {
  it("names the Cup when there is one, otherwise the League", () => {
    expect(formatName('GREAT', FANTASY)).toBe('Fantasy Cup')
    expect(formatName('ULTRA', null)).toBe('Ultra League')
  })
})

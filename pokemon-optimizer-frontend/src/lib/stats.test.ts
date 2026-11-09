import { describe, expect, it } from 'vitest'
import { formatStat } from './stats'

describe('formatStat', () => {
  it('shows two decimals', () => {
    expect(formatStat(110.3456)).toBe('110.35')
  })

  it('rounds a threshold up so the shown number is enough', () => {
    expect(formatStat(113.2101, true)).toBe('113.22')
    expect(formatStat(113.2, true)).toBe('113.20')
  })
})

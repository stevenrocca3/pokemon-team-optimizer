import { describe, expect, it } from 'vitest'
import { IMPORT_DEMO_MS, importDemoFrame } from './import-demo'

describe('importDemoFrame', () => {
  it('fills the appraisal bars, then flashes the screenshot', () => {
    expect(importDemoFrame(0)).toMatchObject({ phase: 'appraise', bars: 0, flash: false })
    expect(importDemoFrame(900)).toMatchObject({ phase: 'appraise', bars: 0.5 })
    expect(importDemoFrame(2000)).toMatchObject({ phase: 'appraise', bars: 1, flash: true })
  })

  it('ticks three screenshots one at a time, then taps Add', () => {
    expect(importDemoFrame(2600)).toMatchObject({ phase: 'pick', picked: 0, tapAdd: false })
    expect(importDemoFrame(3500)).toMatchObject({ phase: 'pick', picked: 2 })
    expect(importDemoFrame(4500)).toMatchObject({ phase: 'pick', picked: 3, tapAdd: true })
  })

  it('scans, then lists the readings one by one, never more than three', () => {
    expect(importDemoFrame(5600)).toMatchObject({ phase: 'read', scan: 0.5, rows: 0 })
    expect(importDemoFrame(6500)).toMatchObject({ phase: 'read', scan: 1, rows: 2 })
    expect(importDemoFrame(7500)).toMatchObject({ rows: 3 })
  })

  it('pops the saved Pokémon in, then loops', () => {
    expect(importDemoFrame(8000)).toMatchObject({ phase: 'saved', shown: 2 })
    expect(importDemoFrame(IMPORT_DEMO_MS + 900)).toMatchObject({ phase: 'appraise', bars: 0.5 })
  })
})

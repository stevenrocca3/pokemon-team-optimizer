import { describe, expect, it } from 'vitest'
import { hp } from '../cp'
import { readBars } from './bars'
import { cpBoxes, hpBarRow, textBoxes, whiteTextMask } from './layout'
import { speciesForName } from './names'
import { solve, type Candidate } from './solve'
import { appraise, type OcrField } from './appraise'
import { appraisalScreen, blank, fillRect, RED_BACKGROUND } from './test-image'

const GUZZLORD: Candidate = { id: 'guzzlord', name: 'Guzzlord', baseAtk: 188, baseDef: 99, baseSta: 440 }
const RESHIRAM: Candidate = { id: 'reshiram', name: 'Reshiram', baseAtk: 275, baseDef: 211, baseSta: 205 }
const SPECIES = [
  { id: 'meowth', name: 'Meowth', dex: 52, shadowEligible: true },
  { id: 'meowth_alolan', name: 'Meowth (Alolan)', dex: 52, shadowEligible: true },
  { id: 'meowth_galarian', name: 'Meowth (Galarian)', dex: 52, shadowEligible: false },
  { id: 'mew', name: 'Mew', dex: 151, shadowEligible: false },
  { id: 'mewtwo', name: 'Mewtwo', dex: 150, shadowEligible: true },
  { id: 'flabebe', name: 'Flabébé', dex: 669, shadowEligible: false },
  { id: 'mr_mime', name: 'Mr. Mime', dex: 122, shadowEligible: false },
  { id: 'guzzlord', name: 'Guzzlord', dex: 799, shadowEligible: false },
]

describe('hp', () => {
  it('is the stamina times the CP multiplier, rounded down', () => {
    // the Guzzlord in IMG_0011: 10/15/10 at level 20 shows 268 HP
    expect(hp(GUZZLORD, { atk: 10, def: 15, sta: 10 }, 20)).toBe(268)
  })
})

describe('readBars', () => {
  it('reads the three bars as IVs out of 15', () => {
    expect(readBars(appraisalScreen([10, 15, 10]))).toEqual([10, 15, 10])
    expect(readBars(appraisalScreen([0, 7, 1]))).toEqual([0, 7, 1])
  })

  it('reads the same bars on another screen size', () => {
    expect(readBars(appraisalScreen([4, 13, 14], 1080, 2316))).toEqual([4, 13, 14])
  })

  it('is null without three bars, so it is not an appraisal screen', () => {
    expect(readBars(blank(1170, 2532, RED_BACKGROUND))).toBeNull()
  })
})

describe('layout', () => {
  it('finds the green HP bar under the name', () => {
    expect(hpBarRow(appraisalScreen([1, 2, 3]))).toBe(Math.round(0.452 * 2532))
  })

  it('reads the name above the HP bar and the HP below it', () => {
    const boxes = textBoxes(1170, 2532, 1144)
    expect(boxes.name.y + boxes.name.height).toBeLessThanOrEqual(1144)
    expect(boxes.hp.y).toBeGreaterThanOrEqual(1144)
    expect(boxes.hp.height).toBeGreaterThan(40) // tall enough for "268 / 268 HP"
  })

  it('looks for the CP near the top of the screen', () => {
    for (const box of cpBoxes(1170, 2532)) expect(box.y + box.height).toBeLessThan(0.12 * 2532)
  })

  it('turns white text into black on white for OCR', () => {
    const p = blank(4, 1, [40, 40, 40])
    fillRect(p, 1, 0, 2, 1, [250, 250, 250])
    const mask = whiteTextMask(p, { x: 0, y: 0, width: 4, height: 1 }, 225)
    expect(Array.from(mask.data.filter((_, i) => i % 4 === 0))).toEqual([255, 0, 255, 255])
  })
})

describe('speciesForName', () => {
  it('matches every form of the species on screen', () => {
    expect(speciesForName('Meowth', SPECIES).map((s) => s.id)).toEqual(['meowth', 'meowth_alolan', 'meowth_galarian'])
  })

  it('ignores accents, punctuation and OCR junk after the name', () => {
    expect(speciesForName('Flabebe', SPECIES).map((s) => s.id)).toEqual(['flabebe'])
    expect(speciesForName('Mr. Mime', SPECIES).map((s) => s.id)).toEqual(['mr_mime'])
    expect(speciesForName('Guzzlord(@s)(@5)', SPECIES).map((s) => s.id)).toEqual(['guzzlord'])
  })

  it('takes the longest name that fits, so Mewtwo is not Mew', () => {
    expect(speciesForName('Mewtwo', SPECIES).map((s) => s.id)).toEqual(['mewtwo'])
  })

  it('finds nothing for a nickname or an empty read', () => {
    expect(speciesForName('Sparky', SPECIES)).toEqual([])
    expect(speciesForName('', SPECIES)).toEqual([])
  })
})

describe('solve', () => {
  const guzzlord = { atk: 10, def: 15, sta: 10 }

  it('finds the level where these IVs give the CP and HP on screen', () => {
    expect(solve([GUZZLORD], [1600], 268, guzzlord)).toEqual([{ speciesId: 'guzzlord', level: 20, cp: 1600 }])
  })

  it('tries each CP reading and keeps the one that fits', () => {
    expect(solve([GUZZLORD], [600, 1600], 268, guzzlord)).toEqual([{ speciesId: 'guzzlord', level: 20, cp: 1600 }])
  })

  it('finds nothing when the bars, CP and HP disagree', () => {
    expect(solve([GUZZLORD], [1600], 268, { atk: 15, def: 15, sta: 15 })).toEqual([])
    expect(solve([GUZZLORD, RESHIRAM], [1601], 268, guzzlord)).toEqual([])
  })
})

describe('appraise', () => {
  const fakeOcr =
    (texts: Partial<Record<OcrField, string>>) =>
    async (_p: unknown, field: OcrField) =>
      texts[field] ?? ''
  const details = async (id: string) => [GUZZLORD, RESHIRAM].find((c) => c.id === id)!
  const deps = (texts: Partial<Record<OcrField, string>>) => ({ ocr: fakeOcr(texts), species: SPECIES, details })

  it('verifies a screen whose bars, CP, HP and name agree', async () => {
    const result = await appraise(appraisalScreen([10, 15, 10]), deps({ cp: 'CP1600', name: 'Guzzlord', hp: '268 / 268 HP' }))
    expect(result).toEqual({
      status: 'verified',
      speciesId: 'guzzlord',
      ivs: { atk: 10, def: 15, sta: 10 },
      level: 20,
      cp: 1600,
      hp: 268,
    })
  })

  it('asks for the CP when it cannot be read', async () => {
    const result = await appraise(appraisalScreen([10, 15, 10]), deps({ cp: '', name: 'Guzzlord', hp: '268 / 268 HP' }))
    expect(result).toMatchObject({ status: 'check', reason: 'cp', ivs: { atk: 10, def: 15, sta: 10 }, hp: 268, speciesIds: ['guzzlord'] })
  })

  it('asks for the species when the name is a nickname', async () => {
    const result = await appraise(appraisalScreen([10, 15, 10]), deps({ cp: 'CP1600', name: 'Chomper', hp: '268 / 268 HP' }))
    expect(result).toMatchObject({ status: 'check', reason: 'species', cps: [1600] })
  })

  it('says so when there are no appraisal bars', async () => {
    const result = await appraise(blank(1170, 2532, RED_BACKGROUND), deps({}))
    expect(result).toEqual({ status: 'not-appraisal' })
  })
})

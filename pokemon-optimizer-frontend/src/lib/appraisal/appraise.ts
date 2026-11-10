import type { Ivs } from '../cp'
import type { SpeciesOption } from '../../types'
import { readBars } from './bars'
import { cpBoxes, hpBarRow, textBoxes, whiteTextMask } from './layout'
import { speciesForName } from './names'
import { crop, type Pixels } from './pixels'
import { solve, type Candidate } from './solve'

export type OcrField = 'cp' | 'name' | 'hp'

export interface AppraiseDeps {
  /** Reads one line of text from these pixels; `field` says which, so OCR can limit the characters it expects. */
  ocr: (pixels: Pixels, field: OcrField) => Promise<string>
  species: SpeciesOption[]
  /** Base stats for a species id (fetched once and cached by the caller). */
  details: (id: string) => Promise<Candidate>
}

export type Appraisal =
  | { status: 'not-appraisal' }
  | { status: 'verified'; speciesId: string; ivs: Ivs; level: number; cp: number; hp: number | null }
  | {
      status: 'check'
      /** what the player should confirm: the CP (unreadable or misread) or the species (a nickname, or unreadable) */
      reason: 'cp' | 'species'
      ivs: Ivs
      hp: number | null
      cps: number[]
      speciesIds: string[]
      nameRead: string
    }

// CP readings to try, in order: the plain crop first (usually right), then white-text masks for bright backgrounds
const CP_PREPS = [null, 200, 225] as const

const parseCp = (text: string) => {
  const m = /(\d{2,4})\s*$/.exec(text.replace(/CP/gi, '').trim())
  return m ? Number(m[1]) : null
}

const parseHp = (text: string) => {
  const m = /(\d+)\s*\/\s*(\d+)/.exec(text)
  return m ? Number(m[2]) : null
}

/**
 * Reads an appraisal screenshot: the bars give the IVs, OCR gives the CP, name and HP, and the result is only
 * "verified" when one species and level reproduce that exact CP and HP. Otherwise it says what to check.
 */
export async function appraise(p: Pixels, deps: AppraiseDeps): Promise<Appraisal> {
  const bars = readBars(p)
  if (!bars) return { status: 'not-appraisal' }
  const ivs: Ivs = { atk: bars[0], def: bars[1], sta: bars[2] }

  const row = hpBarRow(p)
  let nameRead = ''
  let hp: number | null = null
  if (row !== null) {
    const boxes = textBoxes(p.width, p.height, row)
    nameRead = (await deps.ocr(crop(p, boxes.name), 'name')).trim()
    hp = parseHp(await deps.ocr(crop(p, boxes.hp), 'hp'))
  }
  const species = speciesForName(nameRead, deps.species)
  const candidates = await Promise.all(species.map((s) => deps.details(s.id)))

  // read the CP a few ways, stopping as soon as a reading fits
  const cps: number[] = []
  for (const box of cpBoxes(p.width, p.height)) {
    for (const prep of CP_PREPS) {
      const cp = parseCp(await deps.ocr(prep === null ? crop(p, box) : whiteTextMask(p, box, prep), 'cp'))
      if (cp === null || cps.includes(cp)) continue
      cps.push(cp)
      const matches = solve(candidates, [cp], hp, ivs)
      if (matches.length === 1) return { status: 'verified', speciesId: matches[0].speciesId, ivs, level: matches[0].level, cp, hp }
    }
  }

  const check = { status: 'check' as const, ivs, hp, cps, nameRead }
  if (species.length === 0) return { ...check, reason: 'species', speciesIds: [] }
  const matches = solve(candidates, cps, hp, ivs)
  const fitting = [...new Set(matches.map((m) => m.speciesId))]
  // several forms fit (same stats under different names): the player picks; otherwise the CP needs a look
  if (fitting.length > 1) return { ...check, reason: 'species', speciesIds: fitting }
  return { ...check, reason: 'cp', speciesIds: species.map((s) => s.id) }
}

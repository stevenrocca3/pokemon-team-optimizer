import { hp as hpAt, levelsForCp, type BaseStats, type Ivs } from '../cp'

/** A species the screen might show, with the base stats to check it against. */
export interface Candidate extends BaseStats {
  id: string
  name: string
}

export interface Match {
  speciesId: string
  level: number
  cp: number
}

/**
 * Every species and level at which these IVs give one of the CP readings and the HP on screen (any HP when it couldn't
 * be read). An empty list means the readings disagree, which almost always means the CP was misread.
 */
export function solve(candidates: Candidate[], cps: number[], hp: number | null, ivs: Ivs): Match[] {
  const matches: Match[] = []
  for (const c of candidates) {
    for (const cp of cps) {
      for (const level of levelsForCp(c, ivs, cp)) {
        if (hp !== null && hpAt(c, ivs, level) !== hp) continue
        if (!matches.some((m) => m.speciesId === c.id && m.level === level)) matches.push({ speciesId: c.id, level, cp })
      }
    }
  }
  return matches
}

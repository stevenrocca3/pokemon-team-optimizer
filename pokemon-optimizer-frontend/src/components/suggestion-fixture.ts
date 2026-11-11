import type { Suggestion } from '../types'

/** A Team Builder swap suggestion: Medicham-like, replacing Clodsire (request index 2). */
export function suggestion(speciesId: string, role: Suggestion['role'], extra: Partial<Suggestion> = {}): Suggestion {
  return {
    speciesId,
    shadow: false,
    role,
    replaces: 2,
    replacesSpeciesId: 'clodsire',
    level: 41.5,
    cp: 1496,
    atkIv: 1,
    defIv: 15,
    staIv: 12,
    fastMove: 'COUNTER',
    chargedMoves: ['ICE_PUNCH'],
    coverageGain: 34.5,
    covers: ['azumarill', 'annihilape_shadow'],
    ...extra,
  }
}

import { describe, expect, it } from 'vitest'
import type { SpeciesDetail } from '../../types'
import { resolve, toImportPreview } from './review'

const GUZZLORD = { id: 'guzzlord', name: 'Guzzlord', baseAtk: 188, baseDef: 99, baseSta: 440 }
const IVS = { atk: 10, def: 15, sta: 10 }

describe('resolve', () => {
  it('finds the level when the species and CP fit the bars and HP', () => {
    expect(resolve(IVS, 268, GUZZLORD, 1600)).toEqual({ kind: 'ready', level: 20 })
  })

  it('reports a CP that no level gives', () => {
    expect(resolve(IVS, 268, GUZZLORD, 1601)).toEqual({ kind: 'mismatch' })
  })

  it('reports a CP that fits a level whose HP is not the one on screen', () => {
    expect(resolve(IVS, 300, GUZZLORD, 1600)).toEqual({ kind: 'mismatch' })
  })

  it('waits for both a species and a CP', () => {
    expect(resolve(IVS, 268, undefined, 1600)).toEqual({ kind: 'incomplete' })
    expect(resolve(IVS, 268, GUZZLORD, null)).toEqual({ kind: 'incomplete' })
  })
})

describe('toImportPreview', () => {
  const learnset = { fastMoves: ['DRAGON_TAIL'], chargedMoves: ['SLUDGE_BOMB', 'BRUTAL_SWING'], eliteMoves: [] }
  const detail = (id: string, suggested: boolean): SpeciesDetail => ({
    ...GUZZLORD,
    id,
    dex: 799,
    learnset,
    shadowEligible: false,
    suggestedMoves: suggested ? { fastMove: 'DRAGON_TAIL', chargedMoves: ['SLUDGE_BOMB'] } : null,
  })

  it('numbers the Pokémon from 1, asks for every moveset, and carries learnsets and suggestions', () => {
    const details = new Map([
      ['guzzlord', detail('guzzlord', true)],
      ['missingno', detail('missingno', false)],
    ])
    const preview = toImportPreview(
      [
        { speciesId: 'guzzlord', ivs: IVS, level: 20, shadow: false },
        { speciesId: 'missingno', ivs: { atk: 0, def: 1, sta: 2 }, level: 31.5, shadow: true },
      ],
      details,
    )
    expect(preview.pokemon).toEqual([
      { index: 1, speciesId: 'guzzlord', atkIv: 10, defIv: 15, staIv: 10, level: 20, shadow: false, fastMove: null, chargedMoves: [], needsMoves: true },
      { index: 2, speciesId: 'missingno', atkIv: 0, defIv: 1, staIv: 2, level: 31.5, shadow: true, fastMove: null, chargedMoves: [], needsMoves: true },
    ])
    expect(preview.rejected).toEqual([])
    expect(preview.learnsets).toEqual({ guzzlord: learnset, missingno: learnset })
    expect(preview.suggestedMoves).toEqual({ guzzlord: { fastMove: 'DRAGON_TAIL', chargedMoves: ['SLUDGE_BOMB'] } })
  })
})

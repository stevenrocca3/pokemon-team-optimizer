import { describe, expect, it } from 'vitest'
import { artworkUrl, iconUrl } from './species'

describe('Pokémon image URLs', () => {
  it('uses the small 96px sprite for icons (about 1 KB instead of 100–200 KB)', () => {
    expect(iconUrl(308)).toBe('https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/308.png')
  })

  it('keeps the official artwork for large images', () => {
    expect(artworkUrl(308)).toBe(
      'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/308.png',
    )
  })
})

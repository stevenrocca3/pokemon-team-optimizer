import { describe, expect, it } from 'vitest'
import { iconUrl } from './species'

describe('Pokémon image URLs', () => {
  // the site serves PokéAPI's 96px sprites itself (public/sprites), so no image comes from GitHub
  it('uses the self-hosted sprite', () => {
    expect(iconUrl(308)).toBe('/sprites/308.png')
  })
})

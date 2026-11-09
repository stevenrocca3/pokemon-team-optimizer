import { useState } from 'react'
import { artworkUrl, iconUrl } from '../lib/species'

interface Props {
  /** Pokédex number; undefined while the species list is still loading. */
  dex?: number
  alt: string
  className?: string
  /** The page's main image (its LCP element): load it right away at high priority instead of lazily. */
  priority?: boolean
  /** For images shown at about 56px or smaller: the tiny game sprite instead of the full artwork. */
  icon?: boolean
}

export function PokemonArt({ dex, alt, className = '', priority = false, icon = false }: Props) {
  const [failedDex, setFailedDex] = useState<number | null>(null)
  // if a sprite is ever missing, fall back to the artwork before giving up
  const [spriteFailedDex, setSpriteFailedDex] = useState<number | null>(null)
  const useSprite = icon && spriteFailedDex !== dex

  if (dex === undefined || failedDex === dex) {
    return (
      <div className={`grid place-items-center text-faint ${className}`} aria-hidden="true">
        <svg viewBox="0 0 24 24" className="h-1/2 w-1/2" fill="none" stroke="currentColor" strokeWidth="1.5">
          <circle cx="12" cy="12" r="9" />
          <path d="M3 12h18" />
          <circle cx="12" cy="12" r="2.5" fill="currentColor" />
        </svg>
      </div>
    )
  }

  return (
    <img
      src={useSprite ? iconUrl(dex) : artworkUrl(dex)}
      alt={alt}
      loading={priority ? 'eager' : 'lazy'}
      fetchPriority={priority ? 'high' : 'auto'}
      decoding="async"
      onError={() => (useSprite ? setSpriteFailedDex(dex) : setFailedDex(dex))}
      // sprites are pixel art: keep the pixels crisp when scaled
      className={`object-contain ${useSprite ? '[image-rendering:pixelated]' : ''} ${className}`}
    />
  )
}

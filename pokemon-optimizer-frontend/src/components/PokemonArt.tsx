import { useState, type CSSProperties } from 'react'
import { artworkUrl, iconUrl } from '../lib/species'
import { fitTransform, opaqueBounds } from '../lib/sprite-fit'

/** Each sprite's fill-the-box transform, measured once per page load. */
const fits = new Map<number, CSSProperties>()

function measure(img: HTMLImageElement): CSSProperties {
  const size = img.naturalWidth
  if (size === 0 || img.naturalHeight !== size) return {}
  try {
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = size
    const context = canvas.getContext('2d', { willReadFrequently: true })
    if (!context) return {}
    context.drawImage(img, 0, 0)
    const bounds = opaqueBounds(context.getImageData(0, 0, size, size).data, size, size)
    if (!bounds) return {}
    const { scale, x, y } = fitTransform(bounds, size)
    return { transformOrigin: 'top left', transform: `translate(${x}%, ${y}%) scale(${scale})` }
  } catch {
    return {}   // a tainted canvas (no CORS): show the sprite as drawn
  }
}

interface Props {
  /** Pokédex number; undefined while the species list is still loading. */
  dex?: number
  alt: string
  className?: string
  /** The page's main image (its LCP element): load it right away at high priority instead of lazily. */
  priority?: boolean
  /** The pixel game sprite, fitted to its box. The default everywhere, so every Pokémon on the site matches;
   *  false shows the smooth official artwork instead. */
  icon?: boolean
}

export function PokemonArt({ dex, alt, className = '', priority = false, icon = true }: Props) {
  const [failedDex, setFailedDex] = useState<number | null>(null)
  // if a sprite is ever missing, fall back to the artwork before giving up
  const [spriteFailedDex, setSpriteFailedDex] = useState<number | null>(null)
  const useSprite = icon && spriteFailedDex !== dex
  const [, setMeasured] = useState(0)

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
      // sprites are measured so every Pokémon fills its box the same, whatever its in-game size
      crossOrigin={useSprite ? 'anonymous' : undefined}
      style={useSprite ? fits.get(dex) : undefined}
      onLoad={(e) => {
        if (!useSprite || fits.has(dex)) return
        fits.set(dex, measure(e.currentTarget))
        setMeasured((n) => n + 1)
      }}
      onError={() => (useSprite ? setSpriteFailedDex(dex) : setFailedDex(dex))}
      // sprites are pixel art: keep the pixels crisp when scaled
      className={`object-contain ${useSprite ? '[image-rendering:pixelated]' : ''} ${className}`}
    />
  )
}

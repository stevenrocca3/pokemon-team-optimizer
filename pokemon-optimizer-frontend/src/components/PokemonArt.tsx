import { useState, type CSSProperties } from 'react'
import { iconUrl } from '../lib/species'
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
    return {}   // a canvas that can't be read: show the sprite as drawn
  }
}

interface Props {
  /** Pokédex number; undefined while the species list is still loading. */
  dex?: number
  alt: string
  className?: string
  /** The page's main image (its LCP element): load it right away at high priority instead of lazily. */
  priority?: boolean
}

/** A Pokémon's pixel game sprite, fitted to its box so every Pokémon on the site matches; a placeholder if it's missing. */
export function PokemonArt({ dex, alt, className = '', priority = false }: Props) {
  const [failedDex, setFailedDex] = useState<number | null>(null)
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
      src={iconUrl(dex)}
      alt={alt}
      loading={priority ? 'eager' : 'lazy'}
      fetchPriority={priority ? 'high' : 'auto'}
      decoding="async"
      // sprites are measured so every Pokémon fills its box the same, whatever its in-game size
      style={fits.get(dex)}
      onLoad={(e) => {
        if (fits.has(dex)) return
        fits.set(dex, measure(e.currentTarget))
        setMeasured((n) => n + 1)
      }}
      onError={() => setFailedDex(dex)}
      // sprites are pixel art: keep the pixels crisp when scaled
      className={`object-contain [image-rendering:pixelated] ${className}`}
    />
  )
}

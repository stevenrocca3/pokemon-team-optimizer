import { useState } from 'react'
import { artworkUrl } from '../lib/species'

interface Props {
  /** Pokédex number; undefined while the species list is still loading. */
  dex?: number
  alt: string
  className?: string
  /** The page's main image (its LCP element): load it right away at high priority instead of lazily. */
  priority?: boolean
}

export function PokemonArt({ dex, alt, className = '', priority = false }: Props) {
  const [failedDex, setFailedDex] = useState<number | null>(null)

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
      src={artworkUrl(dex)}
      alt={alt}
      loading={priority ? 'eager' : 'lazy'}
      fetchPriority={priority ? 'high' : 'auto'}
      decoding="async"
      onError={() => setFailedDex(dex)}
      className={`object-contain ${className}`}
    />
  )
}

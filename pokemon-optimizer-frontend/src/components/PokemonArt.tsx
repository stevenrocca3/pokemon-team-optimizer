import { useState } from 'react'
import { artworkUrl } from '../lib/species'

interface Props {
  /** Pokédex number; undefined while the species list is still loading. */
  dex?: number
  alt: string
  className?: string
}

export function PokemonArt({ dex, alt, className = '' }: Props) {
  const [failedDex, setFailedDex] = useState<number | null>(null)

  if (dex === undefined || failedDex === dex) {
    return (
      <div className={`grid place-items-center text-zinc-600 ${className}`} aria-hidden="true">
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
      loading="lazy"
      onError={() => setFailedDex(dex)}
      className={`object-contain ${className}`}
    />
  )
}

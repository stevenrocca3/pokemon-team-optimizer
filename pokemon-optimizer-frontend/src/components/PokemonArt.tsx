import { useState } from 'react'
import { artworkUrl } from '../lib/species'

interface Props {
  id: string
  alt: string
  className?: string
}

export function PokemonArt({ id, alt, className = '' }: Props) {
  const [failedId, setFailedId] = useState<string | null>(null)

  if (failedId === id) {
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
      src={artworkUrl(id)}
      alt={alt}
      loading="lazy"
      onError={() => setFailedId(id)}
      className={`object-contain ${className}`}
    />
  )
}

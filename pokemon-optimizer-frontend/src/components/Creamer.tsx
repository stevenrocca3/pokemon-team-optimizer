/**
 * Coffee creamer accents. CreamerRule: a coffee bean with creamer swirling out of it, drawn under titles; the swirls
 * pour themselves in (see .creamer-pour in index.css). Centred under the home page's section titles, or bean first
 * and one swirl trailing right under page titles.
 */
export function CreamerRule({ align = 'center', className = '' }: { align?: 'center' | 'left'; className?: string }) {
  return (
    <div
      className={`flex items-center gap-1.5 ${align === 'center' ? 'justify-center' : ''} ${className}`}
      aria-hidden="true"
    >
      {align === 'center' && <CreamerSwirl />}
      <svg viewBox="0 0 20 28" className="h-5 w-4 shrink-0 fill-brand">
        <ellipse cx="10" cy="14" rx="9" ry="13" />
        <path d="M10 2 C5 9 15 19 10 26" fill="none" className="stroke-canvas" strokeWidth="2" />
      </svg>
      <CreamerSwirl mirrored />
    </div>
  )
}

/** One swirl, drawn from the bean outward and ending in a little curl. */
function CreamerSwirl({ mirrored = false }: { mirrored?: boolean }) {
  return (
    <svg viewBox="0 0 140 24" className={`creamer-pour h-5 w-[7.3rem] shrink-0 overflow-visible ${mirrored ? '-scale-x-100' : ''}`} fill="none">
      <path
        d="M138 12 C122 4 112 20 96 12 S70 4 54 12 S30 20 18 12 C10 7 4 10 6 15 C8 19 15 17 13 12"
        pathLength={1}
        strokeWidth="3"
        strokeLinecap="round"
        className="stroke-creamer"
      />
    </svg>
  )
}

/**
 * Title-screen text: tall pixel capitals in bands of creamer, crema and caramel with a thick espresso outline and a
 * glint that sweeps across (.logo-text in index.css). For short words; screen readers get the text once.
 */
export function LogoText({ text, className = '' }: { text: string; className?: string }) {
  return (
    <span className={`logo-text ${className}`}>
      <span className="logo-text-outline" aria-hidden="true">
        {text}
      </span>
      <span className="logo-text-fill">{text}</span>
    </span>
  )
}

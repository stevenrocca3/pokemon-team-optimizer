/** Espresso cup styled as a Poké Ball: crema top, cream bottom, band and button, steam above. */
export function LogoMark({ className = '' }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={className} aria-hidden="true">
      <g className="stroke-ink-soft" fill="none" strokeWidth="1.6" strokeLinecap="round">
        <path className="steam" d="M11 10.5c-1.6-1.8 1.6-3.2 0-5" />
        <path className="steam" d="M17 10.5c-1.6-1.8 1.6-3.2 0-5" />
      </g>
      <path className="stroke-brand" d="M23 15h1.5a3.5 3.5 0 0 1 0 7h-2.1" fill="none" strokeWidth="2" />
      <path className="fill-brand" d="M5 13h18v6H5z" />
      <path className="fill-ink" d="M23 19a9 8 0 0 1-18 0z" />
      <path className="stroke-canvas" d="M5 19h18" strokeWidth="1.6" />
      <circle className="fill-ink stroke-canvas" cx="14" cy="19" r="2.6" strokeWidth="1.6" />
      <path className="stroke-muted" d="M3.5 29.5h21" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  )
}

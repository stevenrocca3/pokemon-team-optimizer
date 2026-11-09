import { useEffect, useState } from 'react'

// What the server is doing, in order (ADR 0005). Shown as a list, not ticked off: the request reports
// no progress, so pretending to know which step it's on would be made up.
const STEPS = [
  'Battling each of your Pokémon 1v1 against every meta Pokémon',
  'Ranking teams of your ten strongest by how well they cover the meta',
  'Trying every lineup in full 3v3s against 100 meta teams',
  'Measuring win odds on 400 fresh battles',
]

/** Shown while the Team Builder simulates: an espresso shot pulling, and what's being worked out. */
export function Brewing({ startedAt }: { startedAt: number }) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 500)
    return () => clearInterval(id)
  }, [])
  const seconds = Math.floor((now - startedAt) / 1000)

  return (
    <section role="status" aria-live="polite" className="card mt-8 grid items-center gap-8 p-6 sm:grid-cols-[auto_1fr] sm:p-8">
      <EspressoShot />
      <div>
        <p className="eyebrow">Brewing</p>
        <h2 className="mt-1 font-display text-2xl">
          Simulating Full 3v3s <span className="tabular-nums text-muted">· {seconds}s</span>
        </h2>
        <ol className="mt-4 grid gap-2 text-sm text-ink-soft">
          {STEPS.map((step, i) => (
            <li key={step} className="flex gap-3">
              <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-raised text-xs font-bold text-brand ring-1 ring-line">
                {i + 1}
              </span>
              <span className="pt-0.5">{step}</span>
            </li>
          ))}
        </ol>
        <div className="mt-5 h-1.5 overflow-hidden rounded-full bg-raised" aria-hidden="true">
          <div className="h-full w-1/3 animate-[shimmer_1.6s_ease-in-out_infinite] rounded-full bg-gradient-to-r from-transparent via-brand to-transparent" />
        </div>
        <p className="mt-2 text-xs text-subtle">Usually 5–15 seconds; bigger boxes take longer.</p>
      </div>
    </section>
  )
}

/** A cup filling from the portafilter, crema on top, steam rising. Decorative. */
function EspressoShot() {
  return (
    <svg viewBox="0 0 120 120" className="mx-auto h-32 w-32 sm:h-40 sm:w-40" aria-hidden="true">
      <defs>
        <clipPath id="cup-inside">
          <path d="M30 52 h56 v26 a22 22 0 0 1 -22 22 h-12 a22 22 0 0 1 -22 -22 z" />
        </clipPath>
        <linearGradient id="shot" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#c88a3e" />
          <stop offset="0.12" stop-color="#7a4520" />
          <stop offset="1" stop-color="#3a1d0e" />
        </linearGradient>
      </defs>

      {/* portafilter spout and the stream */}
      <rect x="48" y="6" width="20" height="8" rx="2" fill="#4a3a2f" />
      <line x1="58" y1="14" x2="58" y2="60" stroke="#7a4520" strokeWidth="3" strokeLinecap="round" strokeDasharray="4 5" className="animate-[pour_0.6s_linear_infinite]" />

      {/* the shot: rises once to just under the rim and stays */}
      <g clipPath="url(#cup-inside)">
        <rect x="28" y="100" width="62" height="60" fill="url(#shot)" className="animate-[fill_9s_cubic-bezier(0.3,0.6,0.3,1)_forwards]" />
        {/* then creamer swirls down into it */}
        <path
          d="M36 60 C48 54 62 66 76 59 C86 54 82 72 66 73 C52 74 48 66 57 64 C63 63 64 69 60 70"
          pathLength={1}
          fill="none"
          strokeWidth="3.5"
          strokeLinecap="round"
          className="stroke-creamer opacity-90 [stroke-dasharray:1] [stroke-dashoffset:1] animate-[pour-in_2.4s_ease-out_3.5s_forwards]"
        />
      </g>

      {/* cup and handle */}
      <path d="M30 52 h56 v26 a22 22 0 0 1 -22 22 h-12 a22 22 0 0 1 -22 -22 z" fill="none" stroke="#f3e9dc" strokeWidth="3" />
      <path d="M86 60 h6 a9 9 0 0 1 0 18 h-6" fill="none" stroke="#f3e9dc" strokeWidth="3" />
      <ellipse cx="58" cy="106" rx="34" ry="4" fill="#f3e9dc" opacity="0.25" />

      {/* steam */}
      <g fill="none" stroke="#f3e9dc" strokeWidth="2.5" strokeLinecap="round" opacity="0.6">
        <path d="M44 46 c-4 -6 4 -10 0 -16" className="animate-[steam_2.4s_ease-in-out_infinite]" />
        <path d="M72 46 c-4 -6 4 -10 0 -16" className="animate-[steam_2.4s_ease-in-out_0.8s_infinite]" />
      </g>
    </svg>
  )
}

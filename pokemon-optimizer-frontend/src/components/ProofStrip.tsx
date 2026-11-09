import { useEffect, useRef, useState } from 'react'
import { getSiteStats } from '../api'
import { proofStats, type ProofStat } from '../lib/site-stats'
import { prefersReducedMotion } from '../lib/motion'
import { pixelCorners } from '../lib/pixel'

/**
 * The site's real totals (GET /api/stats), as proof people use it. Only numbers past their bar in lib/site-stats.ts
 * appear; with none, or if the request fails, the strip isn't there at all.
 */
export function ProofStrip() {
  const [shown, setShown] = useState<ProofStat[]>([])
  useEffect(() => {
    let live = true
    getSiteStats()
      .then((stats) => live && setShown(proofStats(stats)))
      .catch(() => {}) // a missing strip beats an error on the home page
    return () => {
      live = false
    }
  }, [])
  if (shown.length === 0) return null

  return (
    <section aria-label="DexPresso in numbers" className="border-y border-line bg-surface/70 py-10">
      <ul className={`mx-auto grid max-w-5xl gap-4 px-4 sm:grid-cols-2 ${shown.length >= 3 ? 'lg:grid-cols-4' : ''} justify-center`}>
        {shown.map((stat) => (
          <li
            key={stat.label}
            className="bg-raised px-5 py-4 text-center inset-shadow-[0_3px_0] inset-shadow-creamer/25"
            style={pixelCorners(4)}
          >
            <CountUp stat={stat} />
            <p className="mt-1 text-sm text-ink-soft">{stat.label}</p>
          </li>
        ))}
      </ul>
    </section>
  )
}

/** Counts up from zero the first time it scrolls into view; shows the number at once with reduced motion. */
function CountUp({ stat }: { stat: ProofStat }) {
  const ref = useRef<HTMLParagraphElement>(null)
  const [t, setT] = useState(() => (prefersReducedMotion() ? 1 : 0))
  useEffect(() => {
    const el = ref.current
    if (!el || prefersReducedMotion()) return
    let frame = 0
    const observer = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return
      observer.disconnect()
      const start = performance.now()
      const step = (now: number) => {
        const k = Math.min(1, (now - start) / 1400)
        setT(1 - Math.pow(1 - k, 3)) // ease-out cubic
        if (k < 1) frame = requestAnimationFrame(step)
      }
      frame = requestAnimationFrame(step)
    })
    observer.observe(el)
    return () => {
      observer.disconnect()
      cancelAnimationFrame(frame)
    }
  }, [])

  const decimals = stat.decimals ?? 0
  const now = t === 1 ? stat.value : Math.floor(stat.value * t * 10 ** decimals) / 10 ** decimals
  return (
    <p ref={ref} className="font-display text-4xl text-brand">
      <span>
        {now.toLocaleString(undefined, { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}
      </span>
      {/* the body font: Pixelify's 5 reads like an S */}
      <span className="ml-0.5 font-sans text-3xl font-bold text-creamer">{stat.suffix}</span>
    </p>
  )
}

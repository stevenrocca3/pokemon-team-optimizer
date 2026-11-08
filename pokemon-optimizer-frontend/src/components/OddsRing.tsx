import { useEffect, useState } from 'react'
import { prefersReducedMotion } from '../lib/motion'

const R = 52
const CIRCUMFERENCE = 2 * Math.PI * R

/**
 * The win odds as a ring: the solid arc is the estimate, the faint band the 95% interval around it.
 * The number counts up when it first appears (instantly with reduced motion).
 */
export function OddsRing({ rate, low, high, delayMs = 0 }: { rate: number; low: number; high: number; delayMs?: number }) {
  const shown = useCountUp(rate, delayMs)
  const arc = (from: number, to: number) => ({
    strokeDasharray: `${Math.max(0, to - from) * CIRCUMFERENCE} ${CIRCUMFERENCE}`,
    strokeDashoffset: -from * CIRCUMFERENCE,
  })

  return (
    <div className="relative h-32 w-32 shrink-0">
      <svg viewBox="0 0 120 120" className="h-full w-full -rotate-90" aria-hidden="true">
        <circle cx="60" cy="60" r={R} fill="none" strokeWidth="10" className="stroke-raised" />
        <circle cx="60" cy="60" r={R} fill="none" strokeWidth="10" className="stroke-brand/30" style={arc(low, high)} />
        <circle
          cx="60"
          cy="60"
          r={R}
          fill="none"
          strokeWidth="10"
          strokeLinecap="round"
          className="stroke-brand transition-[stroke-dasharray] duration-1000 ease-out"
          // a round cap on a zero-length arc still draws a dot
          style={{ ...arc(0, shown), opacity: shown < 0.005 ? 0 : 1 }}
        />
      </svg>
      <div className="absolute inset-0 grid place-items-center text-center">
        <div>
          <div className="text-3xl leading-none font-black tabular-nums">{Math.round(shown * 100)}%</div>
          <div className="mt-1 text-[11px] font-semibold tracking-wider text-muted uppercase">win odds</div>
        </div>
      </div>
    </div>
  )
}

function useCountUp(target: number, delayMs: number): number {
  const [value, setValue] = useState(0)
  const still = prefersReducedMotion()
  useEffect(() => {
    if (still) return
    let frame = 0
    let start: number | null = null
    const duration = 900
    const timer = setTimeout(() => {
      const step = (time: number) => {
        start ??= time
        const t = Math.min(1, (time - start) / duration)
        setValue(target * (1 - Math.pow(1 - t, 3)))   // ease-out cubic
        if (t < 1) frame = requestAnimationFrame(step)
      }
      frame = requestAnimationFrame(step)
    }, delayMs)
    return () => {
      clearTimeout(timer)
      cancelAnimationFrame(frame)
    }
  }, [target, delayMs, still])
  return still ? target : value
}

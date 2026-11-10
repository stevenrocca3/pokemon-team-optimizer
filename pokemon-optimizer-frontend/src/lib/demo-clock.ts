import { useEffect, useState, type RefObject } from 'react'
import { prefersReducedMotion } from './motion'

/** Milliseconds of a demo played: runs only while it's on screen; with reduced motion it stays at `stillMs` (a finished frame). */
export function useDemoClock(ref: RefObject<HTMLElement | null>, stillMs: number): number {
  const [ms, setMs] = useState(() => (prefersReducedMotion() ? stillMs : 0))
  useEffect(() => {
    if (prefersReducedMotion() || !ref.current) return
    let timer = 0
    let last = 0
    const tick = () => {
      const now = performance.now()
      setMs((m) => m + (now - last))
      last = now
    }
    const observer = new IntersectionObserver(([entry]) => {
      window.clearInterval(timer)
      if (entry.isIntersecting) {
        last = performance.now()
        timer = window.setInterval(tick, 40)
      }
    })
    observer.observe(ref.current)
    return () => {
      observer.disconnect()
      window.clearInterval(timer)
    }
  }, [ref])
  return ms
}

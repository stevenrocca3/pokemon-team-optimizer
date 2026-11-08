import { useEffect, useRef, useState, type CSSProperties, type ReactNode, type RefObject } from 'react'
import { prefersReducedMotion } from '../lib/motion'

/** Fades and lifts its children in the first time they scroll into view. Shown at once with reduced motion. */
export function Reveal({
  children,
  delayMs = 0,
  className = '',
  as: Tag = 'div',
}: {
  children: ReactNode
  delayMs?: number
  className?: string
  as?: 'div' | 'li' | 'section'
}) {
  const ref = useRef<HTMLElement>(null)
  // nothing to animate with reduced motion, so it starts shown
  const [shown, setShown] = useState(() => prefersReducedMotion() || !('IntersectionObserver' in window))

  useEffect(() => {
    const el = ref.current
    if (!el || shown) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setShown(true)
          observer.disconnect()
        }
      },
      { rootMargin: '0px 0px -12% 0px' },
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [shown])

  const style: CSSProperties = { transitionDelay: `${delayMs}ms` }
  return (
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    <Tag ref={ref as RefObject<any>} data-shown={shown} style={style} className={`reveal ${className}`}>
      {children}
    </Tag>
  )
}

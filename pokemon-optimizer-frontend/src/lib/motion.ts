import { useEffect, useRef, type RefObject } from 'react'

/** no matchMedia (jsdom, very old browsers): treat it as reduced, so nothing animates where it can't be asked */
export const prefersReducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? true

/**
 * Writes how far the page has scrolled into a --scroll CSS variable on the element, once per frame,
 * so parallax layers can move with `translateY(calc(var(--scroll) * ...))` without re-rendering React.
 */
export function useScrollVariable<T extends HTMLElement>(): RefObject<T | null> {
  const ref = useRef<T>(null)
  useEffect(() => {
    if (prefersReducedMotion()) return
    let frame = 0
    const update = () => {
      frame = 0
      ref.current?.style.setProperty('--scroll', String(Math.min(window.scrollY, 1200)))
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update)
    }
    update()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => {
      window.removeEventListener('scroll', onScroll)
      cancelAnimationFrame(frame)
    }
  }, [])
  return ref
}

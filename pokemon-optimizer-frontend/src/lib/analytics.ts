import { useEffect } from 'react'
import { useLocation } from 'react-router'

/**
 * Cookieless page-view counting with GoatCounter: no cookies and no stored IPs, so no consent banner is needed.
 * Off unless VITE_GOATCOUNTER_URL is set at build time (e.g. https://dexpresso.goatcounter.com/count).
 */
const ENDPOINT: string | undefined = import.meta.env.VITE_GOATCOUNTER_URL || undefined
export const ANALYTICS_ENABLED = ENDPOINT !== undefined

type GoatCounter = { no_onload?: boolean; count?: (vars: { path: string }) => void }
declare global {
  interface Window {
    goatcounter?: GoatCounter
  }
}

let loaded = false

function load(endpoint: string) {
  if (loaded) return
  loaded = true
  // React Router changes pages without reloads, so counting is done here on every route change instead
  window.goatcounter = { no_onload: true }
  const script = document.createElement('script')
  script.async = true
  script.src = 'https://gc.zgo.at/count.js'
  script.dataset.goatcounter = endpoint
  document.head.appendChild(script)
}

/** Counts each page view by path only (no query strings, which could hold search text). */
export function usePageViews() {
  const { pathname } = useLocation()
  useEffect(() => {
    if (!ENDPOINT) return
    load(ENDPOINT)
    const count = () => window.goatcounter?.count?.({ path: pathname })
    if (window.goatcounter?.count) count()
    else document.querySelector('script[data-goatcounter]')?.addEventListener('load', count, { once: true })
  }, [pathname])
}

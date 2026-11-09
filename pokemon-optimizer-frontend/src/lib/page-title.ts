import { useEffect } from 'react'

const SITE = 'DexPresso'

/**
 * Sets the tab title (and the title search engines see after rendering) for the current page. index.html keeps
 * a static default for link previews and crawlers that don't run JavaScript.
 */
export function usePageTitle(title: string | null) {
  useEffect(() => {
    document.title = title ? `${title} · ${SITE}` : `${SITE} · PvP IV rankings`
  }, [title])
}

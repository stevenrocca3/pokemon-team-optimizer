const KEY = 'dexpresso.guestToken'

/**
 * This browser's guest token (#120): battles analysed while logged out are saved under it until a login claims
 * them. Made on first use (a random UUID) and kept. Null when the browser won't store anything (private window).
 */
export function guestToken(): string | null {
  const existing = existingGuestToken()
  if (existing) return existing
  try {
    const token = crypto.randomUUID()
    localStorage.setItem(KEY, token)
    return token
  } catch {
    return null
  }
}

/** The token if this browser already has one; never makes one. */
export function existingGuestToken(): string | null {
  try {
    return localStorage.getItem(KEY)
  } catch {
    return null
  }
}

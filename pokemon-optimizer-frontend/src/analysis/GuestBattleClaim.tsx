import { useEffect, useRef } from 'react'
import { claimGuestBattles } from '../api'
import { useCollection } from '../collection/collection-context'
import { existingGuestToken } from './guest-token'

/**
 * Once the player is known to be logged in, moves the battles this browser saved as a guest into their account
 * (once per page load). The token stays: logging out and analysing again saves under it again. Renders nothing.
 */
export function GuestBattleClaim() {
  const collection = useCollection()
  const loggedIn = collection.status === 'ready' && collection.storage === 'account'
  const claimed = useRef(false)

  useEffect(() => {
    if (!loggedIn || claimed.current) return
    const token = existingGuestToken()
    if (!token) return
    claimed.current = true
    claimGuestBattles(token).catch(() => {
      claimed.current = false // offline or a blip: the next page load tries again
    })
  }, [loggedIn])

  return null
}

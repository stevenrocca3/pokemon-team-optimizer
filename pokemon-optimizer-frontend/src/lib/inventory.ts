import { useState } from 'react'
import type { Inventory } from './build-cost'

/**
 * What you've told us you have, kept in this browser only: Stardust, and Candy and Candy XL per Candy family
 * ("FAMILY_MUDKIP"). Private windows and blocked storage just start empty each time.
 */
const KEY = 'dexpresso.inventory.v1'

interface Stored {
  stardust: number | null
  families: Record<string, { candy: number | null; xlCandy: number | null }>
}

function load(): Stored {
  try {
    const parsed = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Stored | null
    if (parsed && typeof parsed === 'object' && parsed.families) return parsed
  } catch {
    // unreadable or blocked: start empty
  }
  return { stardust: null, families: {} }
}

function save(stored: Stored) {
  try {
    localStorage.setItem(KEY, JSON.stringify(stored))
  } catch {
    // blocked storage: it just isn't remembered
  }
}

/** Your stock for one Candy family, and a setter that remembers it. Stardust is shared by every family. */
export function useInventory(family: string | null): [Inventory, (next: Inventory) => void] {
  const [stored, setStored] = useState(load)
  const own = family ? stored.families[family] : undefined
  const inventory: Inventory = { candy: own?.candy ?? null, xlCandy: own?.xlCandy ?? null, stardust: stored.stardust }

  function update(next: Inventory) {
    // re-read, so another card's change to Stardust or its own family isn't lost
    const latest = load()
    const merged: Stored = {
      stardust: next.stardust,
      families: family ? { ...latest.families, [family]: { candy: next.candy, xlCandy: next.xlCandy } } : latest.families,
    }
    save(merged)
    setStored(merged)
  }

  return [inventory, update]
}

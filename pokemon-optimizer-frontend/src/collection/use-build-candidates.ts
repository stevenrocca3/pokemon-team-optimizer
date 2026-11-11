import { useMemo } from 'react'
import { buildCandidates, type BuildCandidate } from '../lib/compare'
import { useCollection } from './collection-context'

/** The species in the player's box worth a "Which One to Build" comparison (#118); empty until the box loads. */
export function useBuildCandidates(): BuildCandidate[] {
  const collection = useCollection()
  return useMemo(() => (collection.status === 'ready' ? buildCandidates(collection.list) : []), [collection])
}

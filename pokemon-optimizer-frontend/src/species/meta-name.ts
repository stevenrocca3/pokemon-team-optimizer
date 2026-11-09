import { useSpecies } from './species-context'

/** PvPoke meta IDs mark Shadows with a suffix ("annihilape_shadow"); our species list has the base form. */
export function useMetaName(): (id: string) => string {
  const species = useSpecies()
  return (id: string) => {
    const shadow = id.endsWith('_shadow')
    const base = shadow ? id.slice(0, -'_shadow'.length) : id
    const name = (species.status === 'ready' ? species.byId.get(base)?.name : undefined) ?? base
    return shadow ? `Shadow ${name}` : name
  }
}

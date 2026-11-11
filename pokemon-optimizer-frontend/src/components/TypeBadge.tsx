/** Each type's colour, bright enough to read as text on the dark cards. */
const COLOR: Record<string, string> = {
  normal: '#c6c6a7',
  fire: '#ff9c54',
  water: '#6cb4ff',
  electric: '#f7d02c',
  grass: '#7ed957',
  ice: '#96d9d6',
  fighting: '#ec6a5e',
  poison: '#c77ddb',
  ground: '#e2bf65',
  flying: '#a99cf5',
  psychic: '#ff7fae',
  bug: '#b8c94a',
  rock: '#c9b46b',
  ghost: '#9d86c9',
  dragon: '#8f7cff',
  dark: '#a89488',
  steel: '#b7c3d6',
  fairy: '#f4a6d7',
}

/** A Pokémon type as a small pill: "Water", "Fairy"… */
export function TypeBadge({ type }: { type: string }) {
  const color = COLOR[type] ?? '#bbbbbb'
  return (
    <span
      data-type={type}
      className="rounded px-1 py-px text-[10px] leading-tight font-bold capitalize ring-1"
      style={{ color, backgroundColor: `${color}1f`, boxShadow: `inset 0 0 0 1px ${color}55` }}
    >
      {type.charAt(0).toUpperCase() + type.slice(1)}
    </span>
  )
}

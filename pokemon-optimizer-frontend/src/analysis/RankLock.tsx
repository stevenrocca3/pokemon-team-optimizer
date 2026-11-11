/**
 * The rank lock's "shader" (#125, spec §3.2): a pixel mosaic standing in for the badge, under a frosted layer
 * (backdrop blur(12px) contrast(150%)) at partial strength, so the blocks still read through the haze. The mosaic is
 * a placeholder made here: the server never sends a locked score, so there's nothing real to hide.
 */
const SIZE = 12
const PALETTE = ['var(--color-tier-legend)', 'var(--color-tier-veteran)', 'var(--color-tier-ace)', 'var(--color-brand)']

const CELLS = Array.from({ length: SIZE * SIZE }, (_, i) => {
  const x = i % SIZE
  const y = Math.floor(i / SIZE)
  const d = Math.hypot(x - (SIZE - 1) / 2, y - (SIZE - 1) / 2)
  if (d > SIZE / 2) return null // a round badge
  const ring = d > SIZE / 2 - 1.6 // the badge's rim, brighter
  return {
    x,
    y,
    fill: PALETTE[(x * 7 + y * 13) % PALETTE.length],
    opacity: ring ? 0.95 : 0.35 + ((x * 3 + y * 5) % 5) / 10,
  }
}).filter((c) => c !== null)

export function RankMosaic({ className = '' }: { className?: string }) {
  return (
    <svg data-mosaic viewBox={`0 0 ${SIZE} ${SIZE}`} shapeRendering="crispEdges" aria-hidden className={className}>
      {CELLS.map((c) => (
        <rect key={`${c.x}-${c.y}`} x={c.x} y={c.y} width={1} height={1} fill={c.fill} opacity={c.opacity} />
      ))}
    </svg>
  )
}

export function RankFrost({ className = '' }: { className?: string }) {
  return (
    <div
      data-frost
      aria-hidden
      className={`absolute inset-0 bg-surface/20 backdrop-blur-[12px] backdrop-contrast-150 ${className}`}
    />
  )
}

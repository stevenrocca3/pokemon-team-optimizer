export function IvTriple({ atk, def, sta, large = false }: { atk: number; def: number; sta: number; large?: boolean }) {
  return (
    <div className="flex items-center gap-3">
      {[
        ['Atk', atk],
        ['Def', def],
        ['Sta', sta],
      ].map(([label, value]) => (
        <div key={label} className={large ? 'w-14' : 'w-12'} title={`${label} ${value}`}>
          <div className={`font-semibold tabular-nums ${large ? 'text-2xl' : ''}`}>{value}</div>
          <div className="mt-1 h-1 rounded-full bg-raised">
            <div className="h-1 rounded-full bg-brand" style={{ width: `${(Number(value) / 15) * 100}%` }} />
          </div>
        </div>
      ))}
    </div>
  )
}

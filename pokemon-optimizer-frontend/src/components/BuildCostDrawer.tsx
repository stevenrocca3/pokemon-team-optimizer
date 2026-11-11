import type { TeamMember } from '../types'
import { buildTotals, shortfalls, type Inventory } from '../lib/build-cost'
import { useInventory } from '../lib/inventory'
import { formatLevel } from '../lib/species'

const n = (value: number) => value.toLocaleString('en-US')

/**
 * The build cost drawer on an evolved Team member (#139): each evolution's Candy, item, lure and conditions, the
 * power-up to its League level, the second Charged Move, the totals, and what you're short of from your own stock.
 */
export function BuildCostDrawer({ member, name }: { member: TeamMember; name: (speciesId: string) => string }) {
  const cost = member.evolution?.cost ?? null
  const [inventory, setInventory] = useInventory(cost?.family ?? null)
  if (!cost) return null
  const totals = buildTotals(member)
  const short = shortfalls(totals, inventory)
  const typed = inventory.candy !== null || inventory.xlCandy !== null || inventory.stardust !== null

  return (
    <details className="group/cost mt-2 rounded-lg bg-canvas/50 text-xs ring-1 ring-line">
      <summary className="cursor-pointer select-none px-2.5 py-1.5 font-semibold text-ink-soft hover:text-ink">Build cost</summary>
      <div role="group" aria-label="Build cost" className="space-y-3 px-2.5 pb-2.5 pt-1">
        <Section title="Evolve">
          {cost.steps.map((step) => (
            <li key={step.from + step.into}>
              {name(step.from)} → {name(step.into)}: <span className="tabular-nums text-ink">{n(step.candy)} Candy</span>
              {step.item && (
                <>
                  , <span className="text-ink">{step.itemCount > 1 ? `${n(step.itemCount)} ` : ''}{step.item}</span>
                </>
              )}
              {step.lure && <>, at a <span className="text-ink">{step.lure}</span></>}
              {step.conditions.map((c) => (
                <span key={c} className="block text-subtle">{c}</span>
              ))}
            </li>
          ))}
          {!cost.complete && <li className="text-amber-300">One evolution's cost isn't in the game data yet.</li>}
        </Section>

        <Section title={`Power up to Lv ${formatLevel(member.level)}`}>
          {member.cost ? (
            <li className="tabular-nums">
              {n(member.cost.stardust)} Stardust, {n(member.cost.candy)} Candy
              {member.cost.xlCandy > 0 && <>, {n(member.cost.xlCandy)} Candy XL</>}
            </li>
          ) : (
            <li className="text-subtle">Add its CP in your Collection to price the power-ups.</li>
          )}
        </Section>

        {cost.secondMove && (
          <Section title="Second Charged Move">
            <li className="tabular-nums">
              {n(cost.secondMove.stardust)} Stardust, {n(cost.secondMove.candy)} Candy
            </li>
          </Section>
        )}

        <div className="rounded-md bg-raised/70 p-2 ring-1 ring-line">
          <p className="font-semibold text-ink">
            Total: <span className="tabular-nums">{n(totals.candy)} Candy</span>
            {totals.xlCandy > 0 && <>, <span className="tabular-nums">{n(totals.xlCandy)} Candy XL</span></>},{' '}
            <span className="tabular-nums">{n(totals.stardust)} Stardust</span>
          </p>
          {(totals.items.length > 0 || totals.lures.length > 0) && (
            <p className="mt-0.5 text-ink-soft">
              Plus {[...totals.items.map((i) => (i.count > 1 ? `${n(i.count)} ${i.name}` : i.name)), ...totals.lures].join(', ')}
            </p>
          )}
          {!totals.powerUpKnown && <p className="mt-0.5 text-subtle">Not counting power-ups.</p>}
        </div>

        <Stock inventory={inventory} onChange={setInventory} />
        {short.map((line) => (
          <p key={line} className="font-semibold text-amber-300">{line}</p>
        ))}
        {typed && short.length === 0 && <p className="font-semibold text-emerald-300">You have enough of what you've entered.</p>}
      </div>
    </details>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="eyebrow mb-1 text-[10px] text-subtle">{title}</p>
      <ul className="space-y-1 text-ink-soft">{children}</ul>
    </div>
  )
}

function Stock({ inventory, onChange }: { inventory: Inventory; onChange: (next: Inventory) => void }) {
  const field = (key: keyof Inventory, label: string) => (
    <label className="block text-[10px] font-semibold uppercase tracking-wide text-subtle">
      {label}
      <input
        type="number"
        min={0}
        inputMode="numeric"
        aria-label={`Your ${label}`}
        value={inventory[key] ?? ''}
        onChange={(e) => {
          const value = e.target.value === '' ? null : Math.max(0, Math.floor(Number(e.target.value)))
          onChange({ ...inventory, [key]: Number.isFinite(value) ? value : null })
        }}
        className="field mt-0.5 h-8 w-full px-2 text-xs normal-case tracking-normal"
      />
    </label>
  )
  return (
    <div>
      <p className="mb-1 text-subtle">What you have</p>
      <div className="grid grid-cols-3 items-end gap-2">
        {field('candy', 'Candy')}
        {field('xlCandy', 'Candy XL')}
        {field('stardust', 'Stardust')}
      </div>
    </div>
  )
}

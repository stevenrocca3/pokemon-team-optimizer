import { useState } from 'react'
import { Link } from 'react-router'
import { LOGIN_URL } from '../api'
import { useCollection } from '../collection/collection-context'
import { formatLevel } from '../lib/species'
import { useMoves } from '../moves/moves-context'
import type { SpeciesDetail } from '../types'

// 1, 1.5, 2 … 51: the backend only accepts whole and half levels
const LEVELS = Array.from({ length: 101 }, (_, i) => 1 + i / 2)

// `saved` remembers what was saved, so the same Pokémon can't be re-added by a second click
type Status = { kind: 'idle' } | { kind: 'saving' } | { kind: 'saved'; key: string } | { kind: 'duplicate'; key: string } | { kind: 'error'; message: string }

interface Props {
  speciesId: string
  ivs: { atk: number; def: number; sta: number }
  // null while the species detail is loading (or if it failed): there are no moves to pick yet
  detail: SpeciesDetail | null
}

const SELECT_CLASS =
  'field mt-1 px-2'

export function AddToCollection({ speciesId, ivs, detail }: Props) {
  const moveList = useMoves()
  const collection = useCollection()
  const [level, setLevel] = useState(20)
  // '' means "not chosen yet": fall back to the first legal move once the learnset arrives
  const [fastChoice, setFastChoice] = useState('')
  const [chargedChoice1, setChargedChoice1] = useState('')
  const [chargedChoice2, setChargedChoice2] = useState<string | null>(null) // null = not chosen; '' = only one charged move
  const [shadowChoice, setShadowChoice] = useState(false)
  const [status, setStatus] = useState<Status>({ kind: 'idle' })

  const learnset = detail?.learnset
  // until the player picks, start on PvPoke's suggested moveset (or the learnset's first moves if it has none)
  const suggested = detail?.suggestedMoves
  const fastMove = fastChoice || suggested?.fastMove || learnset?.fastMoves[0] || ''
  const chargedMove1 = chargedChoice1 || suggested?.chargedMoves[0] || learnset?.chargedMoves[0] || ''
  const second = chargedChoice2 ?? suggested?.chargedMoves[1] ?? ''
  const chargedMove2 = second === chargedMove1 ? '' : second
  const shadow = shadowChoice && detail?.shadowEligible === true
  const chargedMoves = chargedMove2 ? [chargedMove1, chargedMove2] : [chargedMove1]

  const key = `${speciesId}|${ivs.atk}|${ivs.def}|${ivs.sta}|${level}|${fastMove}|${chargedMoves.join(',')}|${shadow}`
  const saved = (status.kind === 'saved' || status.kind === 'duplicate') && status.key === key
  const ready = fastMove !== '' && chargedMove1 !== ''

  const nameOf = (id: string) => (moveList.status === 'ready' ? moveList.byId.get(id)?.name : undefined) ?? id
  const label = (id: string) => (learnset?.eliteMoves.includes(id) ? `${nameOf(id)} ★` : nameOf(id))

  // any change makes the form a new, unsaved Pokémon
  function change<T>(set: (value: T) => void) {
    return (value: T) => {
      set(value)
      setStatus({ kind: 'idle' })
    }
  }

  async function save() {
    if (collection.status !== 'ready') return
    setStatus({ kind: 'saving' })
    try {
      const isNew = await collection.add({ speciesId, atkIv: ivs.atk, defIv: ivs.def, staIv: ivs.sta, level, fastMove, chargedMoves, shadow })
      setStatus({ kind: isNew ? 'saved' : 'duplicate', key })
    } catch (error: unknown) {
      setStatus({ kind: 'error', message: error instanceof Error ? error.message : String(error) })
    }
  }

  return (
    <div className="mt-4 border-t border-line pt-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="text-xs uppercase text-subtle">
          Level
          <select
            value={level}
            onChange={(e) => change(setLevel)(Number(e.target.value))}
            className={`${SELECT_CLASS} w-20`}
          >
            {LEVELS.map((l) => (
              <option key={l} value={l}>
                {formatLevel(l)}
              </option>
            ))}
          </select>
        </label>

        {detail?.shadowEligible && (
          <label className="flex h-11 cursor-pointer items-center gap-2 text-sm text-ink-soft">
            <input
              type="checkbox"
              checked={shadowChoice}
              onChange={(e) => change(setShadowChoice)(e.target.checked)}
              className="h-4 w-4 accent-brand"
            />
            Shadow
          </label>
        )}
      </div>

      <div className="mt-3 grid gap-3 sm:grid-cols-3">
        <label className="min-w-0 text-xs uppercase text-subtle">
          Fast Move
          <select
            value={fastMove}
            onChange={(e) => change(setFastChoice)(e.target.value)}
            disabled={!learnset}
            className={`${SELECT_CLASS} w-full`}
          >
            {learnset?.fastMoves.map((m) => (
              <option key={m} value={m}>
                {label(m)}
              </option>
            ))}
          </select>
        </label>
        <label className="min-w-0 text-xs uppercase text-subtle">
          Charged Move
          <select
            value={chargedMove1}
            onChange={(e) => change(setChargedChoice1)(e.target.value)}
            disabled={!learnset}
            className={`${SELECT_CLASS} w-full`}
          >
            {learnset?.chargedMoves.map((m) => (
              <option key={m} value={m}>
                {label(m)}
              </option>
            ))}
          </select>
        </label>
        <label className="min-w-0 text-xs uppercase text-subtle">
          Second Charged Move
          <select
            value={chargedMove2}
            onChange={(e) => change(setChargedChoice2)(e.target.value)}
            disabled={!learnset}
            className={`${SELECT_CLASS} w-full`}
          >
            <option value="">None</option>
            {learnset?.chargedMoves
              .filter((m) => m !== chargedMove1)
              .map((m) => (
                <option key={m} value={m}>
                  {label(m)}
                </option>
              ))}
          </select>
        </label>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-3">
        <button
          onClick={save}
          disabled={!ready || status.kind === 'saving' || saved || collection.status !== 'ready'}
          className="btn btn-primary"
        >
          {status.kind === 'saving' ? 'Adding…' : status.kind === 'saved' && saved ? 'Added ✓' : 'Add to Collection'}
        </button>
        <p role="status" className="text-sm">
          {status.kind === 'saved' && saved && (
            <span className="text-ink-soft">
              Added
              {collection.status === 'ready' && collection.storage === 'browser' && (
                <>
                  {' '}to this browser (
                  <a href={LOGIN_URL} className="text-brand underline">
                    log in
                  </a>{' '}
                  to keep it everywhere)
                </>
              )}
              .{' '}
              <Link to="/collection" className="text-brand underline">
                View Collection
              </Link>
            </span>
          )}
          {status.kind === 'duplicate' && saved && (
            <span className="text-subtle">
              Already in your{' '}
              <Link to="/collection" className="text-brand underline">
                Collection
              </Link>
              .
            </span>
          )}
          {status.kind === 'error' && <span className="text-red-400">Couldn't add ({status.message}).</span>}
        </p>
      </div>
    </div>
  )
}

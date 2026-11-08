import { useState } from 'react'
import { Link } from 'react-router'
import { addToCollection, LOGIN_URL, NotLoggedInError } from '../api'
import { formatLevel } from '../lib/species'
import { useMoves } from '../moves/moves-context'
import type { SpeciesDetail } from '../types'

// 1, 1.5, 2 … 51: the backend only accepts whole and half levels
const LEVELS = Array.from({ length: 101 }, (_, i) => 1 + i / 2)

// `saved` remembers what was saved, so the same Pokémon can't be re-added by a second click
type Status = { kind: 'idle' } | { kind: 'saving' } | { kind: 'saved'; key: string } | { kind: 'logged-out' } | { kind: 'error'; message: string }

interface Props {
  speciesId: string
  ivs: { atk: number; def: number; sta: number }
  // null while the species detail is loading (or if it failed): there are no moves to pick yet
  detail: SpeciesDetail | null
}

const SELECT_CLASS =
  'mt-1 block rounded-md bg-raised px-2 py-1.5 text-base text-ink ring-1 ring-line-strong focus:outline-none focus:ring-brand'

export function AddToCollection({ speciesId, ivs, detail }: Props) {
  const moveList = useMoves()
  const [level, setLevel] = useState(20)
  // '' means "not chosen yet": fall back to the first legal move once the learnset arrives
  const [fastChoice, setFastChoice] = useState('')
  const [chargedChoice1, setChargedChoice1] = useState('')
  const [chargedChoice2, setChargedChoice2] = useState('') // '' = only one charged move
  const [shadowChoice, setShadowChoice] = useState(false)
  const [status, setStatus] = useState<Status>({ kind: 'idle' })

  const learnset = detail?.learnset
  const fastMove = fastChoice || learnset?.fastMoves[0] || ''
  const chargedMove1 = chargedChoice1 || learnset?.chargedMoves[0] || ''
  const chargedMove2 = chargedChoice2 === chargedMove1 ? '' : chargedChoice2
  const shadow = shadowChoice && detail?.shadowEligible === true
  const chargedMoves = chargedMove2 ? [chargedMove1, chargedMove2] : [chargedMove1]

  const key = `${speciesId}|${ivs.atk}|${ivs.def}|${ivs.sta}|${level}|${fastMove}|${chargedMoves.join(',')}|${shadow}`
  const saved = status.kind === 'saved' && status.key === key
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
    setStatus({ kind: 'saving' })
    try {
      await addToCollection({ speciesId, atkIv: ivs.atk, defIv: ivs.def, staIv: ivs.sta, level, fastMove, chargedMoves, shadow })
      setStatus({ kind: 'saved', key })
    } catch (error: unknown) {
      if (error instanceof NotLoggedInError) setStatus({ kind: 'logged-out' })
      else setStatus({ kind: 'error', message: error instanceof Error ? error.message : String(error) })
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
          <label className="flex cursor-pointer items-center gap-2 pb-2 text-sm text-ink-soft">
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
          disabled={!ready || status.kind === 'saving' || saved}
          className="rounded-md bg-brand px-4 py-2 text-sm font-bold text-on-brand transition hover:brightness-110 disabled:opacity-50"
        >
          {status.kind === 'saving' ? 'Adding…' : saved ? 'Added ✓' : 'Add to Collection'}
        </button>
        <p role="status" className="text-sm">
          {saved && (
            <span className="text-ink-soft">
              Added.{' '}
              <Link to="/collection" className="text-brand underline">
                View Collection
              </Link>
            </span>
          )}
          {status.kind === 'logged-out' && (
            <span className="text-ink-soft">
              <a href={LOGIN_URL} className="text-brand underline">
                Log in
              </a>{' '}
              to save Pokémon to your Collection.
            </span>
          )}
          {status.kind === 'error' && <span className="text-red-400">Couldn't add ({status.message}).</span>}
        </p>
      </div>
    </div>
  )
}

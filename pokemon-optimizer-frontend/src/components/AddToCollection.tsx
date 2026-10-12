import { useState } from 'react'
import { Link } from 'react-router'
import { addToCollection } from '../api'
import { formatLevel } from '../lib/species'

// 1, 1.5, 2 … 51: the backend only accepts whole and half levels
const LEVELS = Array.from({ length: 101 }, (_, i) => 1 + i / 2)

type Status = { kind: 'idle' } | { kind: 'saving' } | { kind: 'saved' } | { kind: 'error'; message: string }

interface Props {
  speciesId: string
  ivs: { atk: number; def: number; sta: number }
}

export function AddToCollection({ speciesId, ivs }: Props) {
  const [level, setLevel] = useState(20)
  const [status, setStatus] = useState<Status>({ kind: 'idle' })

  async function save() {
    setStatus({ kind: 'saving' })
    try {
      await addToCollection({ speciesId, atkIv: ivs.atk, defIv: ivs.def, staIv: ivs.sta, level })
      setStatus({ kind: 'saved' })
    } catch (error: unknown) {
      setStatus({ kind: 'error', message: error instanceof Error ? error.message : String(error) })
    }
  }

  return (
    <div className="mt-4 flex flex-wrap items-end gap-3 border-t border-zinc-800 pt-4">
      <label className="text-xs uppercase text-zinc-500">
        Level
        <select
          value={level}
          onChange={(e) => {
            setLevel(Number(e.target.value))
            setStatus({ kind: 'idle' })
          }}
          className="mt-1 block w-20 rounded-md bg-zinc-800 px-2 py-1.5 text-base text-zinc-100 ring-1 ring-zinc-700 focus:outline-none focus:ring-brand"
        >
          {LEVELS.map((l) => (
            <option key={l} value={l}>
              {formatLevel(l)}
            </option>
          ))}
        </select>
      </label>
      <button
        onClick={save}
        disabled={status.kind === 'saving'}
        className="rounded-md bg-brand px-4 py-2 text-sm font-bold text-black transition hover:brightness-110 disabled:opacity-50"
      >
        {status.kind === 'saving' ? 'Adding…' : 'Add to Collection'}
      </button>
      <p role="status" className="text-sm">
        {status.kind === 'saved' && (
          <span className="text-zinc-300">
            Added.{' '}
            <Link to="/collection" className="text-brand underline">
              View Collection
            </Link>
          </span>
        )}
        {status.kind === 'error' && <span className="text-red-400">Couldn't add ({status.message}).</span>}
      </p>
    </div>
  )
}

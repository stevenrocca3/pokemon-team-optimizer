import { useState } from 'react'
import { Link } from 'react-router'
import { addAllToCollection, LOGIN_URL, NotLoggedInError, previewPokeGenieImport } from '../api'
import type { CaughtPokemonRequest, ImportedPokemon, ImportPreview, Learnset } from '../types'
import { useSpecies } from '../species/species-context'
import { useMoves } from '../moves/moves-context'
import { PokemonArt } from '../components/PokemonArt'
import { IvTriple } from '../components/IvTriple'
import { formatLevel } from '../lib/species'

const PAGE_SIZE = 20

type Stage =
  | { kind: 'pick' }
  | { kind: 'reading'; fileName: string }
  | { kind: 'error'; message: string }
  | { kind: 'preview'; fileName: string; preview: ImportPreview }

type SaveStatus = { kind: 'idle' } | { kind: 'saving' } | { kind: 'saved'; count: number } | { kind: 'logged-out' } | { kind: 'error'; message: string }

// what the player picked for a Pokémon that came without moves; '' = not picked
type Picked = { fast: string; charged1: string; charged2: string }

export function ImportPage() {
  const [stage, setStage] = useState<Stage>({ kind: 'pick' })
  const [picked, setPicked] = useState<Record<number, Picked>>({})
  const [save, setSave] = useState<SaveStatus>({ kind: 'idle' })

  async function read(file: File) {
    setStage({ kind: 'reading', fileName: file.name })
    setSave({ kind: 'idle' })
    try {
      const preview = await previewPokeGenieImport(file)
      // keep whatever moves Genie did record, so the player only fills the gaps
      const start: Record<number, Picked> = {}
      for (const p of preview.pokemon) {
        if (p.needsMoves) start[p.index] = { fast: p.fastMove ?? '', charged1: p.chargedMoves[0] ?? '', charged2: p.chargedMoves[1] ?? '' }
      }
      setPicked(start)
      setStage({ kind: 'preview', fileName: file.name, preview })
    } catch (error: unknown) {
      setStage({ kind: 'error', message: error instanceof Error ? error.message : String(error) })
    }
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <h1 className="text-4xl font-black tracking-tight">Import from Poké Genie</h1>
      <p className="mt-2 max-w-2xl text-ink-soft">
        In Poké Genie, open the menu → Back Up &amp; Restore → Export CSV, then choose that file here. Nothing is saved until you
        press Save.
      </p>

      {stage.kind !== 'preview' && (
        <FilePicker
          busy={stage.kind === 'reading'}
          label={stage.kind === 'reading' ? `Reading ${stage.fileName}…` : undefined}
          onFile={read}
        />
      )}

      {stage.kind === 'error' && (
        <div role="alert" className="mt-6 max-w-2xl rounded-lg border border-red-900 bg-red-950/50 p-5 text-red-200">
          <p className="font-semibold">Couldn't read that file</p>
          <p className="mt-1 text-sm text-red-300/80">{stage.message}</p>
        </div>
      )}

      {stage.kind === 'preview' && (
        <Preview
          fileName={stage.fileName}
          preview={stage.preview}
          picked={picked}
          onPick={(index, next) => {
            setPicked((all) => ({ ...all, [index]: next }))
            if (save.kind === 'saved') setSave({ kind: 'idle' })
          }}
          save={save}
          onSave={async (requests) => {
            setSave({ kind: 'saving' })
            try {
              const saved = await addAllToCollection(requests)
              setSave({ kind: 'saved', count: saved.length })
            } catch (error: unknown) {
              if (error instanceof NotLoggedInError) setSave({ kind: 'logged-out' })
              else setSave({ kind: 'error', message: error instanceof Error ? error.message : String(error) })
            }
          }}
          onStartOver={() => {
            setStage({ kind: 'pick' })
            setSave({ kind: 'idle' })
          }}
        />
      )}
    </div>
  )
}

function FilePicker({ busy, label, onFile }: { busy: boolean; label?: string; onFile: (file: File) => void }) {
  const [dragging, setDragging] = useState(false)

  return (
    <label
      onDragOver={(e) => {
        e.preventDefault()
        setDragging(true)
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(e) => {
        e.preventDefault()
        setDragging(false)
        const file = e.dataTransfer.files[0]
        if (file && !busy) onFile(file)
      }}
      className={`mt-8 flex max-w-2xl cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 py-12 text-center transition ${
        dragging ? 'border-brand bg-brand/10' : 'border-line-strong bg-surface hover:border-brand/60'
      } ${busy ? 'pointer-events-none opacity-60' : ''}`}
    >
      <span className="text-lg font-semibold">{label ?? 'Choose your export (.csv)'}</span>
      <span className="mt-1 text-sm text-subtle">or drop it here</span>
      <input
        type="file"
        accept=".csv,text/csv"
        className="sr-only"
        disabled={busy}
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) onFile(file)
          e.target.value = '' // so choosing the same file again still fires
        }}
      />
    </label>
  )
}

function isComplete(pokemon: ImportedPokemon, picked: Record<number, Picked>) {
  if (!pokemon.needsMoves) return true
  const p = picked[pokemon.index]
  return p !== undefined && p.fast !== '' && p.charged1 !== ''
}

function toRequest(pokemon: ImportedPokemon, picked: Record<number, Picked>): CaughtPokemonRequest {
  const p = pokemon.needsMoves ? picked[pokemon.index] : undefined
  const fastMove = p ? p.fast : (pokemon.fastMove ?? '')
  const chargedMoves = p ? [p.charged1, p.charged2].filter((m) => m !== '') : pokemon.chargedMoves
  return {
    speciesId: pokemon.speciesId,
    atkIv: pokemon.atkIv,
    defIv: pokemon.defIv,
    staIv: pokemon.staIv,
    level: pokemon.level,
    shadow: pokemon.shadow,
    fastMove,
    chargedMoves,
  }
}

interface PreviewProps {
  fileName: string
  preview: ImportPreview
  picked: Record<number, Picked>
  onPick: (index: number, next: Picked) => void
  save: SaveStatus
  onSave: (requests: CaughtPokemonRequest[]) => void
  onStartOver: () => void
}

function Preview({ fileName, preview, picked, onPick, save, onSave, onStartOver }: PreviewProps) {
  const [visible, setVisible] = useState(PAGE_SIZE)
  const needing = preview.pokemon.filter((p) => p.needsMoves)
  const ready = preview.pokemon.filter((p) => isComplete(p, picked))
  const stillNeeding = preview.pokemon.length - ready.length
  const readyFromFile = preview.pokemon.length - needing.length

  return (
    <div className="mt-8">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <p className="text-sm text-subtle">
          From <span className="text-ink-soft">{fileName}</span>
        </p>
        <button onClick={onStartOver} className="text-sm text-muted underline-offset-2 hover:text-ink hover:underline">
          Choose a different file
        </button>
      </div>

      <dl className="mt-3 grid gap-3 sm:grid-cols-3">
        <Count label="Ready to save" value={readyFromFile} />
        <Count label="Need moves picked" value={needing.length} note="Genie didn't record their moves" />
        <Count label="Can't import" value={preview.rejected.length} note="Listed at the bottom" />
      </dl>

      <SaveBar ready={ready.length} stillNeeding={stillNeeding} save={save} onSave={() => onSave(ready.map((p) => toRequest(p, picked)))} />

      {needing.length > 0 && (
        <section className="mt-10">
          <h2 className="flex items-center gap-3 text-2xl font-bold">
            <span className="h-7 w-1 rounded bg-brand" aria-hidden="true" />
            Pick moves
            <span className="text-base font-normal text-subtle">
              {needing.filter((p) => isComplete(p, picked)).length.toLocaleString()} of {needing.length.toLocaleString()} done
            </span>
          </h2>
          <p className="mt-1 text-sm text-subtle">Pokémon left without a fast and a charged move aren't saved. You can add them later.</p>
          <ul className="mt-4 grid gap-3 lg:grid-cols-2">
            {needing.slice(0, visible).map((p) => (
              <NeedsMovesCard
                key={p.index}
                pokemon={p}
                learnset={preview.learnsets[p.speciesId]}
                picked={picked[p.index]}
                onPick={(next) => onPick(p.index, next)}
              />
            ))}
          </ul>
          {visible < needing.length && (
            <div className="mt-6 text-center">
              <button
                onClick={() => setVisible((n) => n + PAGE_SIZE * 2)}
                className="rounded-md bg-raised px-5 py-2.5 text-sm font-semibold text-ink transition hover:bg-raised-strong"
              >
                Show more <span className="text-muted">({visible} of {needing.length.toLocaleString()})</span>
              </button>
            </div>
          )}
        </section>
      )}

      {preview.rejected.length > 0 && (
        <details className="mt-10 rounded-lg bg-surface p-4 ring-1 ring-line">
          <summary className="cursor-pointer font-semibold">
            {preview.rejected.length.toLocaleString()} rows can't be imported
          </summary>
          <p className="mt-2 text-sm text-subtle">
            Row numbers match the Index column in your export. "IVs unknown" means Poké Genie couldn't narrow the scan down to one
            IV spread. Rescan those Pokémon to fix it.
          </p>
          <ul className="mt-3 max-h-80 overflow-y-auto text-sm">
            {preview.rejected.map((r) => (
              <li key={r.index} className="flex gap-3 border-t border-line/60 py-1.5">
                <span className="w-14 shrink-0 tabular-nums text-subtle">#{r.index}</span>
                <span className="text-ink-soft">{r.reason}</span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  )
}

function Count({ label, value, note }: { label: string; value: number; note?: string }) {
  return (
    <div className="rounded-lg bg-surface p-4 ring-1 ring-line">
      <dt className="text-xs uppercase tracking-wider text-muted">{label}</dt>
      <dd className="mt-1 text-3xl font-bold tabular-nums">{value.toLocaleString()}</dd>
      {note && <dd className="mt-1 text-xs text-subtle">{note}</dd>}
    </div>
  )
}

function SaveBar({ ready, stillNeeding, save, onSave }: { ready: number; stillNeeding: number; save: SaveStatus; onSave: () => void }) {
  return (
    <div className="sticky bottom-0 z-20 -mx-4 mt-6 border-t border-line bg-canvas/95 px-4 py-4 backdrop-blur sm:static sm:mx-0 sm:rounded-lg sm:border-0 sm:bg-surface sm:ring-1 sm:ring-line">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <button
          onClick={onSave}
          disabled={ready === 0 || save.kind === 'saving' || save.kind === 'saved'}
          className="rounded-md bg-brand px-5 py-2.5 text-sm font-bold text-on-brand transition hover:brightness-110 disabled:opacity-50"
        >
          {save.kind === 'saving'
            ? 'Saving…'
            : save.kind === 'saved'
              ? 'Saved ✓'
              : `Save ${ready.toLocaleString()} Pokémon to Collection`}
        </button>
        <p role="status" className="text-sm">
          {save.kind === 'saved' && (
            <span className="text-ink-soft">
              Added {save.count.toLocaleString()} Pokémon.{' '}
              <Link to="/collection" className="text-brand underline">
                View Collection
              </Link>
            </span>
          )}
          {save.kind === 'logged-out' && (
            <span className="text-ink-soft">
              <a href={LOGIN_URL} className="text-brand underline">
                Log in
              </a>{' '}
              to save, then choose the file again.
            </span>
          )}
          {save.kind === 'error' && <span className="text-red-400">Couldn't save ({save.message}).</span>}
          {save.kind === 'idle' && stillNeeding > 0 && (
            <span className="text-subtle">{stillNeeding.toLocaleString()} still need moves and won't be saved.</span>
          )}
        </p>
      </div>
      <p className="mt-2 text-xs text-subtle">Saving adds to your Collection, so importing the same file twice adds duplicates.</p>
    </div>
  )
}

const SELECT_CLASS =
  'mt-1 block w-full rounded-md bg-raised px-2 py-1.5 text-sm text-ink ring-1 ring-line-strong focus:outline-none focus:ring-brand'

interface CardProps {
  pokemon: ImportedPokemon
  learnset: Learnset | undefined
  picked: Picked
  onPick: (next: Picked) => void
}

function NeedsMovesCard({ pokemon, learnset, picked, onPick }: CardProps) {
  const species = useSpecies()
  const moves = useMoves()
  const option = species.status === 'ready' ? species.byId.get(pokemon.speciesId) : undefined
  const nameOf = (id: string) => (moves.status === 'ready' ? moves.byId.get(id)?.name : undefined) ?? id
  const label = (id: string) => (learnset?.eliteMoves.includes(id) ? `${nameOf(id)} ★` : nameOf(id))
  const done = picked.fast !== '' && picked.charged1 !== ''

  return (
    <li className={`rounded-lg bg-surface p-4 ring-1 ${done ? 'ring-brand/50' : 'ring-line'}`}>
      <div className="flex items-center gap-3">
        <div className="h-14 w-14 shrink-0 rounded-md bg-raised/60 p-1.5">
          <PokemonArt dex={option?.dex} alt="" className="h-full w-full" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="truncate font-semibold">{option?.name ?? pokemon.speciesId}</span>
            {pokemon.shadow && (
              <span className="rounded bg-violet-500/20 px-1.5 py-0.5 text-[10px] font-bold uppercase text-violet-300">Shadow</span>
            )}
            {done && <span className="ml-auto text-sm text-brand" aria-label="Moves picked">✓</span>}
          </div>
          <div className="mt-1 flex items-end gap-4 text-sm">
            <IvTriple atk={pokemon.atkIv} def={pokemon.defIv} sta={pokemon.staIv} />
            <span className="text-subtle">
              Lv <span className="font-semibold tabular-nums text-ink">{formatLevel(pokemon.level)}</span>
            </span>
            <span className="ml-auto text-xs tabular-nums text-subtle">#{pokemon.index}</span>
          </div>
        </div>
      </div>

      <div className="mt-3 grid gap-2 sm:grid-cols-3">
        <label className="min-w-0 text-xs uppercase text-subtle">
          Fast
          <select value={picked.fast} onChange={(e) => onPick({ ...picked, fast: e.target.value })} className={SELECT_CLASS}>
            <option value="">Choose…</option>
            {learnset?.fastMoves.map((m) => (
              <option key={m} value={m}>
                {label(m)}
              </option>
            ))}
          </select>
        </label>
        <label className="min-w-0 text-xs uppercase text-subtle">
          Charged
          <select
            value={picked.charged1}
            onChange={(e) => {
              const charged1 = e.target.value
              onPick({ ...picked, charged1, charged2: picked.charged2 === charged1 ? '' : picked.charged2 })
            }}
            className={SELECT_CLASS}
          >
            <option value="">Choose…</option>
            {learnset?.chargedMoves.map((m) => (
              <option key={m} value={m}>
                {label(m)}
              </option>
            ))}
          </select>
        </label>
        <label className="min-w-0 text-xs uppercase text-subtle">
          2nd Charged
          <select value={picked.charged2} onChange={(e) => onPick({ ...picked, charged2: e.target.value })} className={SELECT_CLASS}>
            <option value="">None</option>
            {learnset?.chargedMoves
              .filter((m) => m !== picked.charged1)
              .map((m) => (
                <option key={m} value={m}>
                  {label(m)}
                </option>
              ))}
          </select>
        </label>
      </div>
    </li>
  )
}

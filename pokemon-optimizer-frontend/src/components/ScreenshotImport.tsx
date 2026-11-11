import { useEffect, useRef, useState } from 'react'
import { getSpeciesDetail } from '../api'
import { appraise, type Appraisal } from '../lib/appraisal/appraise'
import { pixelsOf, readText, stopOcr, warmUpOcr } from '../lib/appraisal/ocr'
import { resolve, toImportPreview, type ReadyPokemon, type Resolution } from '../lib/appraisal/review'
import { pixelCorners } from '../lib/pixel'
import { formatLevel } from '../lib/species'
import { useSpecies } from '../species/species-context'
import type { ImportPreview, SpeciesDetail, SpeciesOption } from '../types'
import { IvTriple } from './IvTriple'
import { PokemonArt } from './PokemonArt'
import { SpeciesPicker } from './QuickAdd'

// thumbnails and resized shares lose too much detail to read the CP and name
const MIN_WIDTH = 600

type Outcome = Appraisal | { status: 'too-small' } | { status: 'unreadable' }

interface Shot {
  key: number
  name: string
  url: string
  outcome: Outcome | null // null until it has been read
  speciesId: string | null // what was read, or what the player picked
  cp: string // the CP box's text
  shadow: boolean
  editing: boolean
}

type Read = Extract<Outcome, { status: 'verified' | 'check' }>
const isRead = (o: Outcome | null): o is Read => o?.status === 'verified' || o?.status === 'check'

function startingChoice(o: Outcome): Pick<Shot, 'speciesId' | 'cp'> {
  if (o.status === 'verified') return { speciesId: o.speciesId, cp: String(o.cp) }
  if (o.status !== 'check') return { speciesId: null, cp: '' }
  // the CP couldn't be confirmed: the name was read, so start on its first match (the plain form, not a Mega), and leave
  // the CP box empty, since a reading that fits nothing is likely wrong
  if (o.reason === 'cp') return { speciesId: o.speciesIds[0] ?? null, cp: '' }
  return { speciesId: o.speciesIds.length === 1 ? o.speciesIds[0] : null, cp: o.cps.length > 0 ? String(o.cps[0]) : '' }
}

const parseCp = (text: string) => (/^\d{2,5}$/.test(text.trim()) ? Number(text.trim()) : null)

/**
 * Reads Pokémon GO appraisal screenshots on the player's device (nothing is uploaded), lets them confirm anything that
 * couldn't be read for certain, then hands the result to the same move picker and save as a CSV import.
 */
export function ScreenshotImport({ onPreview }: { onPreview: (preview: ImportPreview, label: string) => void }) {
  const species = useSpecies()
  const [shots, setShots] = useState<Shot[]>([])
  const [loaded, setLoaded] = useState<Record<string, SpeciesDetail>>({})
  const [continuing, setContinuing] = useState(false)
  const details = useRef(new Map<string, Promise<SpeciesDetail>>())
  const queue = useRef<{ key: number; file: File }[]>([])
  const running = useRef(false)
  const nextKey = useRef(1)
  const urls = useRef(new Set<string>())

  // the OCR download (~7 MB, once) starts when the tab opens, and its memory is freed when the page is left
  useEffect(() => {
    warmUpOcr()
    const made = urls.current
    return () => {
      stopOcr()
      made.forEach((url) => URL.revokeObjectURL(url))
    }
  }, [])

  function detail(id: string): Promise<SpeciesDetail> {
    let found = details.current.get(id)
    if (!found) {
      found = getSpeciesDetail(id)
      details.current.set(id, found)
      found.then(
        (d) => setLoaded((all) => ({ ...all, [id]: d })),
        () => details.current.delete(id),
      )
    }
    return found
  }

  const update = (key: number, change: Partial<Shot>) => setShots((all) => all.map((s) => (s.key === key ? { ...s, ...change } : s)))

  async function pump(list: SpeciesOption[]) {
    if (running.current) return
    running.current = true
    while (queue.current.length > 0) {
      const { key, file } = queue.current.shift()!
      let outcome: Outcome
      try {
        const pixels = await pixelsOf(file)
        outcome = pixels.width < MIN_WIDTH ? { status: 'too-small' } : await appraise(pixels, { ocr: readText, species: list, details: detail })
      } catch {
        outcome = { status: 'unreadable' }
      }
      update(key, { outcome, ...startingChoice(outcome) })
    }
    running.current = false
  }

  function add(files: File[]) {
    if (species.status !== 'ready') return
    const added = files
      .filter((f) => f.type.startsWith('image/'))
      .map((file) => {
        const url = URL.createObjectURL(file)
        urls.current.add(url)
        const shot: Shot = { key: nextKey.current++, name: file.name, url, outcome: null, speciesId: null, cp: '', shadow: false, editing: false }
        queue.current.push({ key: shot.key, file })
        return shot
      })
    setShots((all) => [...all, ...added])
    void pump(species.list)
  }

  function remove(shot: Shot) {
    queue.current = queue.current.filter((q) => q.key !== shot.key)
    URL.revokeObjectURL(shot.url)
    urls.current.delete(shot.url)
    setShots((all) => all.filter((s) => s.key !== shot.key))
  }

  const resolutionOf = (shot: Shot): Resolution | null =>
    isRead(shot.outcome)
      ? resolve(shot.outcome.ivs, shot.outcome.hp, shot.speciesId ? loaded[shot.speciesId] : undefined, parseCp(shot.cp))
      : null

  const ready: ReadyPokemon[] = []
  for (const shot of shots) {
    const r = resolutionOf(shot)
    if (r?.kind === 'ready' && isRead(shot.outcome)) {
      ready.push({ speciesId: shot.speciesId!, ivs: shot.outcome.ivs, level: r.level, shadow: shot.shadow })
    }
  }
  const done = shots.filter((s) => s.outcome !== null).length
  const reading = done < shots.length

  async function proceed() {
    setContinuing(true)
    try {
      const map = new Map(await Promise.all(ready.map(async (p) => [p.speciesId, await detail(p.speciesId)] as const)))
      onPreview(toImportPreview(ready, map), `${shots.length} screenshot${shots.length === 1 ? '' : 's'}`)
    } finally {
      setContinuing(false)
    }
  }

  return (
    <div>
      <ShotPicker compact={shots.length > 0} disabled={species.status !== 'ready'} onFiles={add} />

      {shots.length > 0 && (
        <>
          <div className="mt-6 flex flex-wrap items-center gap-x-4 gap-y-2" role="status">
            <div className="h-3 w-48 overflow-hidden bg-raised ring-1 ring-line-strong" style={pixelCorners(2)}>
              <div className="h-full bg-brand transition-[width] duration-300" style={{ width: `${(100 * done) / shots.length}%` }} />
            </div>
            <span className="font-display text-sm text-ink-soft">
              {reading ? `Reading ${done + 1} of ${shots.length}…` : `Read ${shots.length} screenshot${shots.length === 1 ? '' : 's'}`}
            </span>
            {reading && done === 0 && <span className="text-xs text-subtle">The first one takes longest while the reader loads.</span>}
          </div>

          <ul className="mt-4 grid gap-3 xl:grid-cols-2">
            {shots.map((shot) => (
              <ShotCard
                key={shot.key}
                shot={shot}
                resolution={resolutionOf(shot)}
                onChange={(change) => {
                  if (change.speciesId) void detail(change.speciesId).catch(() => {})
                  update(shot.key, change)
                }}
                onRemove={() => remove(shot)}
              />
            ))}
          </ul>

          <div className="sticky bottom-0 z-20 -mx-4 mt-6 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-line bg-canvas/95 px-4 py-4 backdrop-blur sm:static sm:mx-0 sm:rounded-lg sm:border-0 sm:bg-surface sm:ring-1 sm:ring-line">
            <button onClick={proceed} disabled={reading || ready.length === 0 || continuing} className="btn btn-primary">
              {continuing ? 'Loading Moves…' : `Pick Moves for ${ready.length.toLocaleString()} Pokémon`}
            </button>
            <p className="text-sm text-subtle">
              {reading
                ? 'Still reading…'
                : ready.length < shots.length
                  ? `${(shots.length - ready.length).toLocaleString()} need a look or can't be used, and won't be imported.`
                  : 'All read. Next you pick moves, then save.'}
            </p>
          </div>
        </>
      )}
    </div>
  )
}

function ShotPicker({ compact, disabled, onFiles }: { compact: boolean; disabled: boolean; onFiles: (files: File[]) => void }) {
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
        if (!disabled) onFiles([...e.dataTransfer.files])
      }}
      className={`mx-auto flex max-w-2xl cursor-pointer lg:mx-0 flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 text-center transition ${
        compact ? 'mt-6 py-5' : 'sword-shine mt-8 py-12'
      } ${dragging ? 'border-brand bg-brand/10' : compact ? 'border-line-strong bg-surface hover:border-brand/60' : 'border-brand/60 bg-surface hover:border-brand hover:bg-brand/5'} ${disabled ? 'pointer-events-none opacity-60' : ''}`}
    >
      {!compact && (
        <>
          <PixelStar className="shine-star top-3 right-5 h-7 w-7" />
          <PixelStar className="shine-star bottom-4 left-6 h-4 w-4 [animation-delay:-0.25s]" />
        </>
      )}
      <span className="text-lg font-semibold">{compact ? 'Add more screenshots' : 'Choose appraisal screenshots'}</span>
      <span className="mt-1 text-sm text-subtle">or drop them here. Pick as many as you like.</span>
      <input
        type="file"
        accept="image/*"
        multiple
        className="sr-only"
        disabled={disabled}
        onChange={(e) => {
          onFiles([...(e.target.files ?? [])])
          e.target.value = ''
        }}
      />
    </label>
  )
}

/** A four-point twinkle in pixels, for the shine on the picker. */
export function PixelStar({ className }: { className: string }) {
  const rows = ['...C...', '...C...', '..CWC..', 'CCWWWCC', '..CWC..', '...C...', '...C...']
  return (
    <svg viewBox="0 0 7 7" className={className} shapeRendering="crispEdges" aria-hidden="true">
      {rows.flatMap((row, y) =>
        [...row].map((c, x) => (c === '.' ? null : <rect key={`${x}-${y}`} x={x} y={y} width="1" height="1" fill={c === 'W' ? '#fffaf0' : '#f1dcc0'} />)),
      )}
    </svg>
  )
}

const BADGE = 'rounded px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider'

function ShotCard({
  shot,
  resolution,
  onChange,
  onRemove,
}: {
  shot: Shot
  resolution: Resolution | null
  onChange: (change: Partial<Shot>) => void
  onRemove: () => void
}) {
  const species = useSpecies()
  const pickerInput = useRef<HTMLInputElement>(null)
  const option = shot.speciesId && species.status === 'ready' ? species.byId.get(shot.speciesId) : undefined
  const o = shot.outcome
  const ok = resolution?.kind === 'ready'

  return (
    <li
      className={`flex gap-3 rounded-lg bg-surface p-3 ring-1 ${ok ? 'ring-brand/50' : 'ring-line'}`}
      style={{ animation: 'pop-in 260ms cubic-bezier(0.2, 0.9, 0.3, 1.3) both' }}
    >
      <img src={shot.url} alt="" className="h-36 w-[4.2rem] shrink-0 bg-raised object-cover object-top" style={pixelCorners(3)} />

      <div className="min-w-0 flex-1">
        <div className="flex items-start gap-2">
          <p className="min-w-0 flex-1 truncate text-xs text-subtle" title={shot.name}>
            {shot.name}
          </p>
          <button onClick={onRemove} aria-label={`Remove ${shot.name}`} className="-mt-1 -mr-1 rounded px-1.5 text-lg leading-none text-muted hover:bg-raised hover:text-ink">
            ×
          </button>
        </div>

        {o === null && <p className="mt-3 animate-pulse font-display text-sm text-ink-soft">Reading…</p>}
        {o?.status === 'not-appraisal' && (
          <Problem title="Not an appraisal screen">
            Open the Pokémon, tap ☰ then Appraise, tap until the three bars show, and screenshot that.
          </Problem>
        )}
        {o?.status === 'too-small' && <Problem title="Too small to read">Use the original screenshot, not a thumbnail or a resized copy.</Problem>}
        {o?.status === 'unreadable' && <Problem title="Couldn't open this image">Try saving it as PNG or JPEG.</Problem>}

        {isRead(o) && (
          <>
            <div className="mt-1 flex items-center gap-2">
              <PokemonArt dex={option?.dex} alt="" icon className="h-9 w-9 shrink-0" />
              <span className="truncate font-semibold">{option?.name ?? 'Which Pokémon?'}</span>
              <span className="ml-auto shrink-0">
                {ok ? (
                  <span className={`${BADGE} bg-brand/20 text-brand`}>Ready</span>
                ) : resolution?.kind === 'mismatch' ? (
                  <span className={`${BADGE} bg-red-500/20 text-red-300`}>Doesn't fit</span>
                ) : (
                  <span className={`${BADGE} bg-amber-400/20 text-amber-200`}>Needs a look</span>
                )}
              </span>
            </div>
            <div className="mt-1.5 flex flex-wrap items-end gap-x-4 gap-y-1 text-sm">
              <IvTriple atk={o.ivs.atk} def={o.ivs.def} sta={o.ivs.sta} />
              {ok && (
                <span className="text-subtle">
                  Lv <span className="font-semibold tabular-nums text-ink">{formatLevel(resolution.level)}</span>
                </span>
              )}
              {ok && !shot.editing && (
                <span className="text-subtle">
                  CP <span className="font-semibold tabular-nums text-ink">{shot.cp}</span>
                </span>
              )}
            </div>

            {ok && !shot.editing ? (
              <div className="mt-2 flex items-center gap-3 text-sm">
                {option?.shadowEligible && <ShadowBox checked={shot.shadow} onChange={(shadow) => onChange({ shadow })} />}
                <button onClick={() => onChange({ editing: true })} className="ml-auto text-xs text-muted underline-offset-2 hover:text-ink hover:underline">
                  Not right?
                </button>
              </div>
            ) : (
              <Fixer shot={shot} read={o} option={option} resolution={resolution} pickerInput={pickerInput} onChange={onChange} />
            )}
          </>
        )}
      </div>
    </li>
  )
}

function Fixer({
  shot,
  read,
  option,
  resolution,
  pickerInput,
  onChange,
}: {
  shot: Shot
  read: Read
  option: SpeciesOption | undefined
  resolution: Resolution | null
  pickerInput: React.RefObject<HTMLInputElement | null>
  onChange: (change: Partial<Shot>) => void
}) {
  const species = useSpecies()
  // forms that share base stats (Zigzagoon and Galarian Zigzagoon) can't be told apart from the numbers: one tap picks
  const forms = read.status === 'check' && read.reason === 'species' && read.speciesIds.length > 1 && species.status === 'ready' ? read.speciesIds.map((id) => species.byId.get(id)).filter((s) => s !== undefined) : []
  const hint =
    read.status !== 'check'
      ? null
      : forms.length > 0
        ? 'These forms have the same stats. Which one is it?'
        : read.reason === 'species'
          ? read.nameRead
            ? `Read the name as "${read.nameRead}". A nickname? Pick the Pokémon.`
            : "Couldn't read the name. Pick the Pokémon."
          : "Couldn't read the CP for sure. Type the number at the top of the screenshot."

  return (
    <div className="mt-2">
      {hint && <p className="text-xs text-ink-soft">{hint}</p>}
      {forms.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {forms.map((f) => (
            <button
              key={f.id}
              onClick={() => onChange({ speciesId: f.id })}
              aria-pressed={shot.speciesId === f.id}
              className={`btn btn-sm ${shot.speciesId === f.id ? 'btn-primary' : 'btn-secondary'}`}
            >
              {f.name}
            </button>
          ))}
        </div>
      )}
      <div className="mt-2 grid gap-2 sm:grid-cols-[1fr_7rem]">
        {forms.length === 0 && <SpeciesPicker inputRef={pickerInput} selected={option ?? null} onChoose={(s) => onChange({ speciesId: s.id })} />}
        <label className={`text-[11px] font-semibold tracking-wider text-subtle uppercase ${forms.length > 0 ? 'sm:col-start-2' : ''}`}>
          CP
          <input
            value={shot.cp}
            onChange={(e) => onChange({ cp: e.target.value })}
            inputMode="numeric"
            placeholder="e.g. 1500"
            className="field mt-1 tabular-nums"
          />
        </label>
      </div>
      {resolution?.kind === 'mismatch' && (
        <p className="mt-2 text-xs text-red-300">
          CP {shot.cp} doesn't fit {option?.name ?? 'this Pokémon'} with these IVs{read.hp !== null ? ` and ${read.hp} HP` : ''}. Check the CP and the Pokémon.
        </p>
      )}
      <div className="mt-2 flex items-center gap-3 text-sm">
        {option?.shadowEligible && <ShadowBox checked={shot.shadow} onChange={(shadow) => onChange({ shadow })} />}
        {shot.editing && resolution?.kind === 'ready' && (
          <button onClick={() => onChange({ editing: false })} className="ml-auto text-xs text-muted underline-offset-2 hover:text-ink hover:underline">
            Done
          </button>
        )}
      </div>
    </div>
  )
}

function ShadowBox({ checked, onChange }: { checked: boolean; onChange: (checked: boolean) => void }) {
  return (
    <label className="flex cursor-pointer items-center gap-1.5 text-xs text-ink-soft">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="accent-violet-400" />
      Shadow
    </label>
  )
}

function Problem({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mt-2">
      <p className="font-semibold text-ink-soft">{title}</p>
      <p className="mt-0.5 text-xs text-subtle">{children}</p>
    </div>
  )
}

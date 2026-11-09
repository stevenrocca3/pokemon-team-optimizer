import { useEffect, useId, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { getSpeciesDetail } from '../api'
import { useCollection } from '../collection/collection-context'
import { levelsForCp } from '../lib/cp'
import { formatDex, formatLevel, searchSpecies } from '../lib/species'
import { useMoves } from '../moves/moves-context'
import { useSpecies } from '../species/species-context'
import type { SpeciesDetail, SpeciesOption } from '../types'
import { PokemonArt } from './PokemonArt'

// .field (index.css) gives every control here the same 44px height as the buttons
const INPUT = 'field'
const LABEL = 'text-[11px] font-semibold uppercase tracking-wider text-subtle'

type IvKey = 'atk' | 'def' | 'sta'
const IV_FIELDS: { key: IvKey; label: string }[] = [
  { key: 'atk', label: 'Atk' },
  { key: 'def', label: 'Def' },
  { key: 'sta', label: 'HP' },
]

type Feedback = { kind: 'added' | 'duplicate'; name: string } | { kind: 'error'; message: string } | null

/**
 * The fast way in: name → IVs → CP from the game screen → Enter, then straight on to the next Pokémon.
 * The game shows CP, not level, so the level is worked out from CP and IVs.
 */
export function QuickAdd() {
  const collection = useCollection()
  const moveList = useMoves()
  const [species, setSpecies] = useState<SpeciesOption | null>(null)
  const [detail, setDetail] = useState<SpeciesDetail | null>(null)
  const [ivs, setIvs] = useState<Record<IvKey, string>>({ atk: '', def: '', sta: '' })
  const [cpText, setCpText] = useState('')
  const [levelChoice, setLevelChoice] = useState<number | null>(null)
  const [shadow, setShadow] = useState(false)
  const [moves, setMoves] = useState({ fast: '', charged1: '', charged2: '' })
  const [saving, setSaving] = useState(false)
  const [feedback, setFeedback] = useState<Feedback>(null)

  const speciesInput = useRef<HTMLInputElement>(null)
  const ivInputs = useRef<Record<IvKey, HTMLInputElement | null>>({ atk: null, def: null, sta: null })
  const cpInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!species) return
    let cancelled = false
    getSpeciesDetail(species.id)
      .then((d) => {
        if (cancelled) return
        setDetail(d)
        // start on PvPoke's moveset; most players keep it, and the team builder is only as good as the moves
        const suggested = d.suggestedMoves
        setMoves(
          suggested
            ? { fast: suggested.fastMove, charged1: suggested.chargedMoves[0] ?? '', charged2: suggested.chargedMoves[1] ?? '' }
            : { fast: d.learnset.fastMoves[0] ?? '', charged1: d.learnset.chargedMoves[0] ?? '', charged2: '' },
        )
      })
      .catch(() => {
        if (!cancelled) setFeedback({ kind: 'error', message: `Couldn't load ${species.name}'s moves` })
      })
    return () => {
      cancelled = true
    }
  }, [species])

  const ivValues = { atk: Number(ivs.atk), def: Number(ivs.def), sta: Number(ivs.sta) }
  const ivsDone = IV_FIELDS.every(({ key }) => ivs[key] !== '' && ivValues[key] >= 0 && ivValues[key] <= 15)
  // two digits fit in the box, so 16–99 can be typed: flag the box instead of leaving Add disabled with no reason
  const badIvs = IV_FIELDS.filter(({ key }) => ivs[key] !== '' && ivValues[key] > 15)
  const ivErrorId = useId()
  const cp = Number(cpText)
  const levels = useMemo(
    () => (detail && ivsDone && cpText !== '' && cp >= 10 ? levelsForCp(detail, ivValues, cp) : []),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [detail, ivsDone, ivs.atk, ivs.def, ivs.sta, cpText],
  )
  const level = levels.length === 0 ? null : levels.includes(levelChoice ?? -1) ? levelChoice! : levels[0]
  const ready = species !== null && detail !== null && ivsDone && level !== null && moves.fast !== '' && moves.charged1 !== ''

  const nameOf = (id: string) => (moveList.status === 'ready' ? moveList.byId.get(id)?.name : undefined) ?? id
  const moveLabel = (id: string) => (detail?.learnset.eliteMoves.includes(id) ? `${nameOf(id)} ★` : nameOf(id))

  function choose(option: SpeciesOption) {
    if (option.id !== species?.id) {
      setDetail(null)
      setShadow(false)
    }
    setSpecies(option)
    setFeedback(null)
    ivInputs.current.atk?.focus()
  }

  function typeIv(key: IvKey, raw: string) {
    const text = raw.replace(/\D/g, '').slice(0, 2)
    setIvs((current) => ({ ...current, [key]: text }))
    // a value that can't grow into another IV is complete: 0, 2–9, or two digits (1 might become 10–15)
    const complete = text.length === 2 || (text.length === 1 && text !== '1')
    if (complete) {
      const order: IvKey[] = ['atk', 'def', 'sta']
      const next = order[order.indexOf(key) + 1]
      if (next) ivInputs.current[next]?.select()
      else cpInput.current?.select()
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!ready || collection.status !== 'ready' || level === null) return
    setSaving(true)
    try {
      const chargedMoves = moves.charged2 && moves.charged2 !== moves.charged1 ? [moves.charged1, moves.charged2] : [moves.charged1]
      const isNew = await collection.add({
        speciesId: species.id,
        atkIv: ivValues.atk,
        defIv: ivValues.def,
        staIv: ivValues.sta,
        level,
        fastMove: moves.fast,
        chargedMoves,
        shadow: shadow && detail.shadowEligible,
      })
      setFeedback({ kind: isNew ? 'added' : 'duplicate', name: `${shadow && detail.shadowEligible ? 'Shadow ' : ''}${species.name}` })
      // ready for the next one; the species stays picked in case it's another of the same
      setIvs({ atk: '', def: '', sta: '' })
      setCpText('')
      setLevelChoice(null)
      speciesInput.current?.select()
    } catch (error: unknown) {
      setFeedback({ kind: 'error', message: error instanceof Error ? error.message : String(error) })
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} className="card p-5 sm:p-6" aria-label="Quick add a Pokémon">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-lg font-bold">Add a Pokémon</h2>
        <span className="hidden text-xs text-subtle sm:inline">Name → Enter → IVs → CP → Enter</span>
      </div>

      <div className="mt-3 grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
        <SpeciesPicker inputRef={speciesInput} selected={species} onChoose={choose} />

        <div className="grid grid-cols-[repeat(3,minmax(0,3.5rem))_minmax(0,7rem)] gap-2">
          {IV_FIELDS.map(({ key, label }) => (
            <label key={key}>
              <span className={LABEL}>{label}</span>
              <input
                ref={(el) => {
                  ivInputs.current[key] = el
                }}
                value={ivs[key]}
                onChange={(e) => typeIv(key, e.target.value)}
                onFocus={(e) => e.target.select()}
                inputMode="numeric"
                placeholder="0–15"
                aria-label={`${label} IV`}
                aria-invalid={badIvs.some((f) => f.key === key)}
                aria-describedby={badIvs.length > 0 ? ivErrorId : undefined}
                className={`${INPUT} mt-1 px-1 text-center tabular-nums aria-invalid:ring-2 aria-invalid:ring-danger`}
              />
            </label>
          ))}
          <label>
            <span className={LABEL}>CP</span>
            <input
              ref={cpInput}
              value={cpText}
              onChange={(e) => {
                setCpText(e.target.value.replace(/\D/g, '').slice(0, 5))
                setLevelChoice(null)
              }}
              onFocus={(e) => e.target.select()}
              inputMode="numeric"
              placeholder="in game"
              className={`${INPUT} mt-1 tabular-nums`}
            />
          </label>
        </div>
      </div>

      {badIvs.length > 0 ? (
        <p id={ivErrorId} role="alert" className="mt-2 text-xs text-ink">
          {badIvs.map((f) => f.label).join(' and ')} must be 0 to 15. IVs never go higher.
        </p>
      ) : (
        <LevelLine detail={detail} ivsDone={ivsDone} cpText={cpText} levels={levels} level={level} onChoose={setLevelChoice} />
      )}

      <div className="mt-3 grid gap-3 sm:grid-cols-[repeat(3,minmax(0,1fr))_auto] sm:items-end">
        <MoveSelect
          label="Fast Move"
          value={moves.fast}
          options={detail?.learnset.fastMoves ?? []}
          moveLabel={moveLabel}
          onChange={(fast) => setMoves((m) => ({ ...m, fast }))}
        />
        <MoveSelect
          label="Charged Move"
          value={moves.charged1}
          options={detail?.learnset.chargedMoves ?? []}
          moveLabel={moveLabel}
          onChange={(charged1) => setMoves((m) => ({ ...m, charged1 }))}
        />
        <MoveSelect
          label="Second Charged"
          value={moves.charged2 === moves.charged1 ? '' : moves.charged2}
          options={(detail?.learnset.chargedMoves ?? []).filter((m) => m !== moves.charged1)}
          moveLabel={moveLabel}
          none
          onChange={(charged2) => setMoves((m) => ({ ...m, charged2 }))}
        />
        <label
          className={`flex h-11 items-center gap-2 text-sm ${detail?.shadowEligible ? 'cursor-pointer text-ink-soft' : 'text-subtle opacity-60'}`}
          title={detail && !detail.shadowEligible ? `${detail.name} can't be a Shadow` : undefined}
        >
          <input
            type="checkbox"
            checked={shadow && detail?.shadowEligible === true}
            disabled={!detail?.shadowEligible}
            onChange={(e) => setShadow(e.target.checked)}
            className="h-4 w-4 accent-brand"
          />
          Shadow
        </label>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <button
          type="submit"
          disabled={!ready || saving || collection.status !== 'ready'}
          className="btn btn-primary min-w-28"
        >
          {saving ? 'Adding…' : 'Add'}
        </button>
        <p role="status" className="text-sm">
          {feedback?.kind === 'added' && <span className="text-ink-soft">Added {feedback.name}.</span>}
          {feedback?.kind === 'duplicate' && <span className="text-subtle">{feedback.name} with those IVs is already here.</span>}
          {feedback?.kind === 'error' && <span className="text-red-400">{feedback.message}</span>}
        </p>
      </div>
    </form>
  )
}

function LevelLine({
  detail,
  ivsDone,
  cpText,
  levels,
  level,
  onChoose,
}: {
  detail: SpeciesDetail | null
  ivsDone: boolean
  cpText: string
  levels: number[]
  level: number | null
  onChoose: (level: number) => void
}) {
  if (!detail || !ivsDone || cpText === '') return <p className="mt-2 h-5 text-xs text-subtle" />
  if (levels.length === 0) {
    return (
      <p className="mt-2 min-h-5 text-xs text-red-400">
        No level gives {detail.name} {cpText} CP with these IVs. Check the IVs and CP.
      </p>
    )
  }
  if (levels.length === 1) {
    return (
      <p className="mt-2 min-h-5 text-xs text-ink-soft">
        Level <span className="font-semibold tabular-nums text-ink">{formatLevel(levels[0])}</span>
      </p>
    )
  }
  return (
    <div className="mt-2 flex min-h-5 flex-wrap items-center gap-1.5 text-xs text-ink-soft">
      That CP fits more than one level:
      {levels.map((l) => (
        <button
          key={l}
          type="button"
          onClick={() => onChoose(l)}
          aria-pressed={l === level}
          className={`rounded px-1.5 py-0.5 tabular-nums ring-1 ${l === level ? 'bg-brand/15 text-brand ring-brand/60' : 'ring-line hover:text-ink'}`}
        >
          {formatLevel(l)}
        </button>
      ))}
    </div>
  )
}

function MoveSelect({
  label,
  value,
  options,
  moveLabel,
  none = false,
  onChange,
}: {
  label: string
  value: string
  options: string[]
  moveLabel: (id: string) => string
  none?: boolean
  onChange: (id: string) => void
}) {
  return (
    <label className="min-w-0">
      <span className={LABEL}>{label}</span>
      <select value={value} onChange={(e) => onChange(e.target.value)} disabled={options.length === 0} className={`${INPUT} mt-1`}>
        {none && <option value="">None</option>}
        {options.map((m) => (
          <option key={m} value={m}>
            {moveLabel(m)}
          </option>
        ))}
      </select>
    </label>
  )
}

function SpeciesPicker({
  inputRef,
  selected,
  onChoose,
}: {
  inputRef: React.RefObject<HTMLInputElement | null>
  selected: SpeciesOption | null
  onChoose: (option: SpeciesOption) => void
}) {
  const species = useSpecies()
  const listId = useId()
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)

  const results = useMemo(() => (species.status === 'ready' ? searchSpecies(species.list, query, 6) : []), [species, query])
  const showList = open && query.trim() !== '' && results.length > 0

  function choose(option: SpeciesOption) {
    onChoose(option)
    setQuery('')
    setOpen(false)
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setActive((i) => Math.min(i + 1, results.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((i) => Math.max(i - 1, 0))
    } else if (e.key === 'Enter' && query.trim() !== '') {
      // Enter here picks a species; it must not submit the form
      e.preventDefault()
      const option = results[active] ?? results[0]
      if (option) choose(option)
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  return (
    <div className="relative">
      <label htmlFor={`${listId}-input`} className={LABEL}>
        Pokémon
      </label>
      <div className="mt-1 flex h-11 items-center gap-2 rounded-lg bg-raised pl-1.5 ring-1 ring-line-strong ring-inset focus-within:ring-2 focus-within:ring-brand">
        <PokemonArt dex={selected?.dex} alt="" icon className="h-8 w-8 shrink-0" />
        <input
          id={`${listId}-input`}
          ref={inputRef}
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            setOpen(true)
            setActive(0)
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setOpen(false)}
          onKeyDown={onKeyDown}
          placeholder={selected ? selected.name : species.status === 'ready' ? 'Name or dex #' : 'Loading…'}
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-activedescendant={showList ? `${listId}-${active}` : undefined}
          autoComplete="off"
          className={`h-full min-w-0 flex-1 bg-transparent pr-2 text-base text-ink focus:outline-none ${selected ? 'placeholder:text-ink' : 'placeholder:text-subtle'}`}
        />
      </div>
      {showList && (
        <ul id={listId} role="listbox" className="absolute z-20 mt-1 w-full overflow-hidden rounded-md bg-raised shadow-xl ring-1 ring-line-strong">
          {results.map((option, i) => (
            <li
              key={option.id}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={i === active}
              // mousedown, not click: it fires before the input's blur closes the list
              onMouseDown={(e) => {
                e.preventDefault()
                choose(option)
              }}
              onMouseEnter={() => setActive(i)}
              className={`flex cursor-pointer items-center gap-2 px-2 py-1.5 text-sm ${i === active ? 'bg-brand/15 text-ink' : 'text-ink-soft'}`}
            >
              <PokemonArt dex={option.dex} alt="" icon className="h-7 w-7 shrink-0" />
              <span className="truncate">{option.name}</span>
              <span className="ml-auto text-xs text-subtle">{formatDex(option.dex)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

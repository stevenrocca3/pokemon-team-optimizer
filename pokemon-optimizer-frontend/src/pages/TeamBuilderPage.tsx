import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { buildTeams, getCups, LOGIN_URL, previewImport } from '../api'
import { BuildCostDrawer } from '../components/BuildCostDrawer'
import { useCollection } from '../collection/collection-context'
import type { CaughtPokemon, CaughtPokemonRequest, CupSummary, Evolution, MatchupRef, OwnedSpecies, PowerUpImpact, Suggestion, TeamBuilderLeague, TeamBuilderResult, TeamMember, TeamResult } from '../types'
import { useSpecies } from '../species/species-context'
import { useMoves } from '../moves/moves-context'
import { PokemonArt } from '../components/PokemonArt'
import { IvTriple } from '../components/IvTriple'
import { formatLevel } from '../lib/species'
import { startingMoves } from '../lib/moves'
import { PageHeader } from '../components/PageHeader'
import { BuildCallout } from '../components/BuildCallout'
import { Brewing } from '../components/Brewing'
import { OddsRing } from '../components/OddsRing'
import { usePageTitle } from '../lib/page-title'
import { useMetaName } from '../species/meta-name'
import { groupByOpponent, shieldsList } from '../lib/matchups'
import { bestAffordable, teamCost } from '../lib/budget'
import { cupsFor, formatName } from '../lib/cups'
import type { BattleTeam } from '../analysis/team-nudge'
import { SwapSuggestions } from '../components/SwapSuggestions'
import { suggestionBuild } from '../lib/suggestion'
import { UndoToast, UNDO_MS } from '../components/UndoToast'
import { sameness } from '../collection/merge'

const STARDUST_KEY = 'dexpresso.stardust'

/** The player's Stardust, remembered in this browser only (a convenience; it's fine if storage is blocked). */
function useStardustBudget(): [string, (next: string) => void] {
  const [text, setText] = useState(() => {
    try {
      return localStorage.getItem(STARDUST_KEY) ?? ''
    } catch {
      return ''
    }
  })
  const update = (next: string) => {
    const digits = next.replace(/\D/g, '').slice(0, 9)
    setText(digits)
    try {
      if (digits) localStorage.setItem(STARDUST_KEY, digits)
      else localStorage.removeItem(STARDUST_KEY)
    } catch {
      // storage blocked: the box still works for this visit
    }
  }
  return [text, update]
}

type Cups = { status: 'loading' } | { status: 'error' } | { status: 'ready'; list: CupSummary[] }

// the Cups change at most nightly: fetch them once per visit, and again after a failure
let cupsRequest: Promise<CupSummary[]> | null = null

function useCups(): Cups {
  const [cups, setCups] = useState<Cups>({ status: 'loading' })
  useEffect(() => {
    let cancelled = false
    cupsRequest ??= getCups().catch((error: unknown) => {
      cupsRequest = null
      throw error
    })
    cupsRequest.then(
      (list) => !cancelled && setCups({ status: 'ready', list }),
      () => !cancelled && setCups({ status: 'error' }),
    )
    return () => {
      cancelled = true
    }
  }, [])
  return cups
}

const LEAGUES: { key: TeamBuilderLeague; label: string; cpLabel: string }[] = [
  { key: 'GREAT', label: 'Great', cpLabel: '1500 CP' },
  { key: 'ULTRA', label: 'Ultra', cpLabel: '2500 CP' },
  { key: 'MASTER', label: 'Master', cpLabel: 'No cap' },
]

// Where the Pokémon come from: the saved Collection (exact Pokémon plus checklist ticks), a Poké Genie / Calcy IV
// file read in the browser session only, or the team seen in an analysed battle (its review's import button)
type Source =
  | { kind: 'battle'; names: string[]; pokemon: CaughtPokemonRequest[]; species: OwnedSpecies[]; withoutMoves: number }
  | { kind: 'collection'; pokemon: CaughtPokemonRequest[]; species: OwnedSpecies[]; withoutMoves: number }
  | { kind: 'file'; fileName: string; pokemon: CaughtPokemonRequest[]; species: OwnedSpecies[]; withoutMoves: number }

const sizeOf = (source: Source) => source.pokemon.length + source.species.length

/** The species behind a result index: exact Pokémon first, then the ticks (TeamBuilder.build on the server). */
function speciesAt(source: Source, index: number): string {
  return index < source.pokemon.length ? source.pokemon[index].speciesId : (source.species[index - source.pokemon.length]?.speciesId ?? '')
}

type CollectionState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; inBrowser: boolean; source: Source }

type Run =
  | { status: 'idle' }
  | { status: 'running'; startedAt: number }
  | { status: 'error'; message: string }
  | { status: 'done'; result: TeamBuilderResult; source: Source; name: string }

/** What an Undo puts back (#115): the build shown before the add, the file or battle source, and the add itself. */
type Undo = {
  message: string
  before: Extract<Run, { status: 'done' }>
  fileBefore: Source | null
  adding: Promise<{ pokemon: CaughtPokemon; added: boolean }>
}

export function TeamBuilderPage() {
  usePageTitle('Team Builder')
  const saved = useCollection()
  const collection = useMemo<CollectionState>(() => {
    if (saved.status !== 'ready') return saved
    // Pokémon saved before movesets existed have no moves and can't battle
    const withMoves = saved.list.filter((p) => p.fastMove && p.chargedMoves.length > 0)
    const pokemon = withMoves.map(({ speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow }) => ({
      speciesId, atkIv, defIv, staIv, level, fastMove, chargedMoves, shadow,
    }))
    return {
      status: 'ready',
      inBrowser: saved.storage === 'browser',
      source: { kind: 'collection', pokemon, species: saved.checklist, withoutMoves: saved.list.length - withMoves.length },
    }
  }, [saved])
  // a battle review's "Import Team" arrives with its team and league
  const battle = (useLocation().state as { battleTeam?: BattleTeam } | null)?.battleTeam
  const [fileSource, setFileSource] = useState<Source | null>(() =>
    battle ? { kind: 'battle', names: battle.names, pokemon: [], species: battle.species, withoutMoves: 0 } : null)
  const [useFile, setUseFile] = useState(Boolean(battle))
  const [league, setLeague] = useState<TeamBuilderLeague>(battle?.league ?? 'GREAT')
  const [cupKey, setCupKey] = useState('')
  const [includeEvolvable, setIncludeEvolvable] = useState(false)
  const cups = useCups()
  const [run, setRun] = useState<Run>({ status: 'idle' })
  const abort = useRef<AbortController | null>(null)

  useEffect(() => () => abort.current?.abort(), [])

  const source: Source | null =
    useFile || collection.status !== 'ready' ? fileSource : collection.source
  const leagueCups = cups.status === 'ready' ? cupsFor(cups.list, league) : []
  const cup = leagueCups.find((c) => c.key === cupKey) ?? null

  async function build(from: Source | null = source) {
    if (!from || sizeOf(from) === 0) return
    abort.current?.abort()
    const controller = new AbortController()
    abort.current = controller
    setRun({ status: 'running', startedAt: Date.now() })
    try {
      const result = await buildTeams(
        { league, pokemon: from.pokemon, species: from.species, cup: cup?.key ?? null, includeEvolvable },
        controller.signal,
      )
      setRun({ status: 'done', result, source: from, name: formatName(league, cup) })
    } catch (error: unknown) {
      if (controller.signal.aborted) return
      setRun({ status: 'error', message: error instanceof Error ? error.message : String(error) })
    }
  }

  const [addError, setAddError] = useState<string | null>(null)
  const speciesNames = useSpecies()
  // the Collection as it is now, for an undo that runs after later renders
  const latestCollection = useRef(saved)
  useEffect(() => {
    latestCollection.current = saved
  })

  const [undo, setUndo] = useState<Undo | null>(null)

  useEffect(() => {
    if (!undo) return
    const timer = setTimeout(() => setUndo(null), UNDO_MS)
    return () => clearTimeout(timer)
  }, [undo])

  /**
   * "Add to Box & Re-Analyze" (#114), optimistic (#115): the box, the toast and the rebuild don't wait for the
   * account to confirm the save; the suggestion's build is what the server saves too. If the save fails, it's undone.
   */
  function addSuggestion(suggestion: Suggestion) {
    if (run.status !== 'done' || saved.status !== 'ready') return
    setAddError(null)
    const before = run
    const fileBefore = fileSource
    const request = suggestionBuild(suggestion)
    const from = run.source
    const next = from.pokemon.some((p) => sameness(p) === sameness(request)) ? from : { ...from, pokemon: [...from.pokemon, request] }
    // a battle team or a file isn't the Collection: it keeps the new one too, for the next build
    if (from.kind !== 'collection') setFileSource(next)

    const adding = saved.addSuggestion(suggestion, league, cup?.key ?? null)
    const name = (speciesNames.status === 'ready' ? speciesNames.byId.get(suggestion.speciesId)?.name : undefined) ?? suggestion.speciesId
    const entry: Undo = { message: `Added ${suggestion.shadow ? 'Shadow ' : ''}${name} to your box`, before, fileBefore, adding }
    setUndo(entry)
    adding.catch((error: unknown) => {
      restore(entry)
      setAddError(error instanceof Error ? error.message : String(error))
    })
    void build(next)
  }

  /** Back to the scores and sources from before the add, without building again. */
  function restore(entry: Undo) {
    abort.current?.abort()
    setUndo((current) => (current === entry ? null : current))
    setRun(entry.before)
    if (entry.before.source.kind !== 'collection') setFileSource(entry.fileBefore)
  }

  /** Undo (#115): the scores come back at once; the Pokémon leaves the box once its save has landed. */
  async function undoAdd() {
    if (!undo) return
    const entry = undo
    restore(entry)
    try {
      const { pokemon, added } = await entry.adding
      const collection = latestCollection.current
      // already in the box before? then it stays: Undo only takes back what this add put there
      if (added && collection.status === 'ready') await collection.remove(pokemon.id)
    } catch {
      // the save failed, so there's nothing to take back
    }
  }

  function changeLeague(next: TeamBuilderLeague) {
    abort.current?.abort()
    setLeague(next)
    setCupKey('')
    setRun({ status: 'idle' })
  }

  function changeCup(next: string) {
    abort.current?.abort()
    setCupKey(next)
    setRun({ status: 'idle' })
  }

  const tooFew = !source || sizeOf(source) < 3

  return (
    <div className="mx-auto max-w-6xl px-4 pb-16">
      <PageHeader eyebrow="Team Builder" title="Brew Your Best Three">
        Battles full 3v3s from your Pokémon against teams drawn from the current meta, then tells you how often each team wins.
      </PageHeader>

      <BuildCallout league={league} />

      <section className="card grid gap-px overflow-hidden bg-line lg:grid-cols-[1.4fr_1fr_auto]">
        <Step n={1} title="Your Pokémon">
          <SourcePicker
            collection={collection}
            fileSource={fileSource}
            useFile={useFile}
            onUseFile={setUseFile}
            onFile={(next) => {
              setFileSource(next)
              setUseFile(true)
              setRun({ status: 'idle' })
            }}
          />
          {source && source.pokemon.length > 0 && (
            <label className="mt-4 flex cursor-pointer items-start gap-2.5 rounded-lg bg-raised/60 p-3 text-sm ring-1 ring-line">
              <input
                type="checkbox"
                checked={includeEvolvable}
                onChange={(e) => setIncludeEvolvable(e.target.checked)}
                className="mt-0.5 size-4 shrink-0 accent-brand"
              />
              <span>
                <span className="font-semibold text-ink">Include Pokémon you could evolve</span>
                <span className="mt-0.5 block text-xs text-subtle">
                  Tries your unevolved Pokémon as what they'd evolve into, with the same IVs and its best moves.
                </span>
              </span>
            </label>
          )}
        </Step>

        <Step n={2} title="League or Cup">
          <div role="tablist" aria-label="League" className="grid grid-cols-3 gap-1 rounded-xl bg-raised p-1 ring-1 ring-line">
            {LEAGUES.map((l) => (
              <button
                key={l.key}
                role="tab"
                aria-selected={l.key === league}
                onClick={() => changeLeague(l.key)}
                className={`flex h-14 flex-col items-center justify-center rounded-lg text-sm transition ${
                  l.key === league ? 'bg-brand text-on-brand shadow inset-shadow-[0_3px_0] inset-shadow-creamer/60' : 'text-ink-soft hover:bg-raised-strong hover:text-ink'
                }`}
              >
                <span className="font-bold">{l.label}</span>
                <span className={`text-xs ${l.key === league ? 'font-semibold text-on-brand' : 'text-subtle'}`}>{l.cpLabel}</span>
              </button>
            ))}
          </div>
          <CupPicker cups={cups} leagueCups={leagueCups} league={league} cup={cup} onChange={changeCup} />
        </Step>

        <Step n={3} title="Brew">
          <button onClick={() => build()} disabled={tooFew || run.status === 'running'} className="btn btn-primary btn-lg w-full">
            {run.status === 'running' ? 'Brewing…' : 'Build teams'}
          </button>
          <p className="mt-3 text-xs text-subtle">
            {tooFew ? 'Needs at least 3 Pokémon.' : `${sizeOf(source).toLocaleString()} Pokémon ready`}
          </p>
        </Step>
      </section>

      {run.status === 'running' && <Brewing startedAt={run.startedAt} />}

      {run.status === 'error' && (
        <div role="alert" className="mt-8 max-w-2xl rounded-2xl border border-red-900 bg-red-950/70 p-5 text-red-200">
          <p className="font-semibold">Couldn't build teams</p>
          <p className="mt-1 text-sm text-red-200">{run.message}</p>
        </div>
      )}

      {undo && <UndoToast key={undo.message} message={undo.message} onUndo={undoAdd} />}

      {addError && (
        <p role="alert" className="mt-6 text-sm text-red-400">
          Couldn't add it to your box: {addError}
        </p>
      )}

      {run.status === 'done' && (
        <Results
          result={run.result}
          source={run.source}
          name={run.name}
          onAdd={saved.status === 'ready' ? addSuggestion : undefined}
        />
      )}

      <HowItWorks />
    </div>
  )
}

function CupPicker({
  cups,
  leagueCups,
  league,
  cup,
  onChange,
}: {
  cups: Cups
  leagueCups: CupSummary[]
  league: TeamBuilderLeague
  cup: CupSummary | null
  onChange: (key: string) => void
}) {
  if (cups.status === 'error') {
    return <p className="mt-3 text-xs text-subtle">Cups can't be loaded right now. The open leagues still work.</p>
  }
  return (
    <div className="mt-3">
      <label className="block text-xs font-semibold uppercase tracking-wide text-subtle">
        Cup
        <select
          value={cup?.key ?? ''}
          onChange={(e) => onChange(e.target.value)}
          disabled={cups.status === 'loading'}
          className="field mt-1 w-full normal-case tracking-normal"
        >
          <option value="">{cups.status === 'loading' ? 'Loading Cups…' : `Open ${formatName(league, null)}`}</option>
          {leagueCups.map((c) => (
            <option key={c.key} value={c.key}>
              {c.title}
            </option>
          ))}
        </select>
      </label>
      {cups.status === 'ready' && leagueCups.length === 0 && (
        <p className="mt-2 text-xs text-subtle">No {formatName(league, null)} Cups running right now.</p>
      )}
      {cup && (
        <div className="mt-2 text-xs text-ink-soft">
          {cup.rules.length > 0 && (
            <ul className="list-disc space-y-0.5 pl-4">
              {cup.rules.map((rule) => (
                <li key={rule}>{rule}</li>
              ))}
            </ul>
          )}
          <p className="mt-1 text-subtle">
            {cup.partySize === 6 ? 'Bring 6, battle with 3. ' : ''}
            Rules and meta from PvPoke, {cup.updated}.
          </p>
        </div>
      )}
    </div>
  )
}

function SourcePicker({
  collection,
  fileSource,
  useFile,
  onUseFile,
  onFile,
}: {
  collection: CollectionState
  fileSource: Source | null
  useFile: boolean
  onUseFile: (useFile: boolean) => void
  onFile: (source: Source) => void
}) {
  const [reading, setReading] = useState(false)
  const [fileError, setFileError] = useState<string | null>(null)

  async function read(file: File) {
    setReading(true)
    setFileError(null)
    try {
      const preview = await previewImport(file)
      // a Pokémon the export has no moves for battles with PvPoke's suggested moveset
      const pokemon = preview.pokemon.flatMap((p) => {
        const moves = startingMoves(p, preview.suggestedMoves[p.speciesId])
        if (moves.fast === '' || moves.charged1 === '') return []
        const chargedMoves = moves.charged2 ? [moves.charged1, moves.charged2] : [moves.charged1]
        const { speciesId, atkIv, defIv, staIv, level, shadow } = p
        return [{ speciesId, atkIv, defIv, staIv, level, shadow, fastMove: moves.fast, chargedMoves }]
      })
      onFile({ kind: 'file', fileName: file.name, pokemon, species: [], withoutMoves: preview.pokemon.length - pokemon.length })
    } catch (error: unknown) {
      setFileError(error instanceof Error ? error.message : String(error))
    } finally {
      setReading(false)
    }
  }

  const fileInput = (
    <label className={`btn btn-secondary cursor-pointer ${reading ? 'pointer-events-none opacity-60' : ''}`}>
      {reading ? 'Reading…' : fileSource?.kind === 'file' ? 'Choose another file' : 'Choose a Poké Genie or Calcy IV file'}
      <input
        type="file"
        accept=".csv,text/csv"
        className="sr-only"
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) read(file)
          e.target.value = ''
        }}
      />
    </label>
  )

  return (
    <div>
      {collection.status === 'loading' && <div className="h-11 w-64 animate-pulse rounded-lg bg-raised" />}

      {collection.status === 'ready' && (
        <div className="grid grid-cols-2 gap-2">
          <Choice active={!useFile} onClick={() => onUseFile(false)}>
            My Collection <span className={useFile ? 'text-subtle' : 'text-brand'}>({sizeOf(collection.source).toLocaleString()})</span>
          </Choice>
          <Choice active={useFile} onClick={() => onUseFile(true)}>
            {fileSource?.kind === 'battle' ? 'Your battle team' : 'An export file'}
          </Choice>
        </div>
      )}

      {collection.status === 'error' && (
        <p className="text-sm text-ink-soft">Couldn't load your Collection ({collection.message}). You can still use a file.</p>
      )}

      {(useFile || collection.status === 'error') && (
        <div className="mt-3 flex flex-wrap items-center gap-3">
          {fileInput}
          {fileSource?.kind === 'battle' && (
            <span className="text-sm text-ink-soft">
              From your analysed battle: <span className="text-ink">{fileSource.names.join(', ')}</span>
            </span>
          )}
          {fileSource?.kind === 'file' && (
            <span className="text-sm text-ink-soft">
              {fileSource.pokemon.length.toLocaleString()} Pokémon from <span className="text-ink">{fileSource.fileName}</span>
            </span>
          )}
        </div>
      )}

      {fileError && <p className="mt-2 text-sm text-red-400">{fileError}</p>}

      <SourceNote source={useFile || collection.status !== 'ready' ? fileSource : collection.source} />

      {collection.status === 'ready' && !useFile && sizeOf(collection.source) < 3 && (
        <p className="mt-3 text-sm text-ink-soft">
          {sizeOf(collection.source) === 0 ? 'No Pokémon yet.' : `${sizeOf(collection.source)} so far; a team needs 3.`}{' '}
          <Link to="/collection" className="text-brand underline">
            Tick the ones you have
          </Link>
          , it takes a minute.
        </p>
      )}
      {collection.status === 'ready' && !useFile && collection.source.species.length > 0 && (
        <p className="mt-2 text-xs text-subtle">
          {collection.source.species.length.toLocaleString()} ticked on your checklist battle with good IVs for the league.
        </p>
      )}
      {collection.status === 'ready' && collection.inBrowser && !useFile && (
        <p className="mt-2 text-xs text-subtle">
          Saved in this browser.{' '}
          <a href={LOGIN_URL} className="text-brand underline">
            Log in
          </a>{' '}
          to keep them everywhere.
        </p>
      )}
      {useFile && fileSource?.kind !== 'battle' && <p className="mt-2 text-xs text-subtle">Nothing from the file is saved.</p>}
    </div>
  )
}

function SourceNote({ source }: { source: Source | null }) {
  if (!source || source.withoutMoves === 0) return null
  return (
    <p className="mt-2 text-xs text-subtle">
      {source.withoutMoves.toLocaleString()} without moves are left out
      {source.kind === 'file' ? ' (PvPoke has no suggested moves for them; pick them on the Import page).' : '.'}
    </p>
  )
}

function Choice({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      aria-pressed={active}
      className={`btn ring-1 ring-inset ${
        active ? 'bg-brand/15 text-brand ring-brand' : 'bg-raised text-ink-soft ring-line-strong hover:text-ink'
      }`}
    >
      {children}
    </button>
  )
}

function Step({ n, title, children }: { n: number; title: string; children: React.ReactNode }) {
  return (
    <div className="bg-surface p-5 sm:p-6">
      <div className="mb-4 flex items-center gap-3">
        <span className="grid h-7 w-7 place-items-center rounded-full bg-brand/15 text-sm font-black text-brand ring-1 ring-brand/50">{n}</span>
        <h2 className="font-bold">{title}</h2>
      </div>
      {children}
    </div>
  )
}

function Results({
  result,
  source,
  name,
  onAdd,
}: {
  result: TeamBuilderResult
  source: Source
  name: string
  onAdd?: (suggestion: Suggestion) => void
}) {
  const species = useSpecies()
  const nameOf = (id: string) => (species.status === 'ready' ? species.byId.get(id)?.name : undefined) ?? id
  const [stardustText, setStardust] = useStardustBudget()
  const budget = stardustText === '' ? null : Number(stardustText)
  const affordable = budget === null ? null : bestAffordable(result.teams, budget)
  const anyCost = result.teams.some((t) => t.members.some((m) => m.cost !== null))

  return (
    <section className="mt-12" aria-live="polite">
      {result.teams.length === 0 ? (
        <div className="card max-w-2xl p-6">
          <p className="font-semibold">Not enough Pokémon for {name}</p>
          <p className="mt-1 text-sm text-ink-soft">
            A team needs three different species that {result.cup ? "the Cup's rules allow" : 'fit the league'}. {result.eligible} of
            yours can play there.
          </p>
        </div>
      ) : (
        <>
          <p className="eyebrow text-brand">Fresh from the bar</p>
          <h2 className="mt-1 font-display text-3xl font-black">Best Teams for {name}</h2>
          <p className="mt-1 text-sm text-subtle">
            From {result.eligible.toLocaleString()} eligible · vs {result.metaSize} meta Pokémon (PvPoke, {result.metaDate})
          </p>
          {result.teams.some((t) => t.members.some((m) => m.assumed)) && (
            <p className="mt-3 max-w-2xl text-sm text-ink-soft">
              <span className="font-semibold text-ink">Assumed IVs</span> marks a Pokémon from your checklist, battled with a
              good spread for this league. If yours is weaker, the odds will be a little lower;{' '}
              <Link to="/collection?view=exact" className="text-brand underline">
                add its exact IVs
              </Link>{' '}
              to check.
            </p>
          )}
          {anyCost && (
            <div className="mt-5 flex flex-wrap items-center gap-3">
              <label className="flex items-center gap-2 text-sm text-ink-soft">
                Your Stardust
                <input
                  value={stardustText === '' ? '' : Number(stardustText).toLocaleString()}
                  onChange={(e) => setStardust(e.target.value)}
                  inputMode="numeric"
                  placeholder="e.g. 500,000"
                  className="field h-9 w-36 tabular-nums"
                />
              </label>
              <p className="text-sm text-subtle">
                {budget === null
                  ? 'Add it to see which team you can afford to power up.'
                  : affordable === null
                    ? "None of these fit yet. Costs are from each Pokémon's current level."
                    : affordable === 0
                      ? 'The best team fits your budget.'
                      : `Team ${affordable + 1} is the best one that fits.`}
              </p>
            </div>
          )}
          <ol className="mt-6 grid gap-6">
            {result.teams.map((team, i) => (
              <TeamCard
                key={team.members.map((m) => m.index).join('-')}
                rank={i + 1}
                team={team}
                delayMs={i * 140}
                budget={budget}
                bestForBudget={affordable === i && i > 0}
                onAdd={onAdd}
              />
            ))}
          </ol>
        </>
      )}

      {result.skipped.length > 0 && (
        <details className="card mt-6 max-w-2xl p-4">
          <summary className="cursor-pointer text-sm font-semibold">
            {result.skipped.length.toLocaleString()} Pokémon couldn't play in {name}
          </summary>
          <ul className="mt-3 max-h-72 overflow-y-auto text-sm">
            {result.skipped.map((s) => (
              <li key={s.index} className="flex gap-3 border-t border-line/60 py-1.5">
                <span className="w-40 shrink-0 truncate text-ink-soft">{nameOf(speciesAt(source, s.index))}</span>
                <span className="text-subtle">{s.reason}</span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  )
}

function percent(fraction: number) {
  return Math.round(fraction * 100)
}

function TeamCard({
  rank,
  team,
  delayMs,
  budget,
  bestForBudget,
  onAdd,
}: {
  rank: number
  team: TeamResult
  delayMs: number
  budget: number | null
  bestForBudget: boolean
  onAdd?: (suggestion: Suggestion) => void
}) {
  const metaName = useMetaName()
  const species = useSpecies()
  const nameOf = (id: string) => (species.status === 'ready' ? species.byId.get(id)?.name : undefined) ?? id
  const { rate, low, high, battles } = team.winRate
  const cost = teamCost(team.members)
  const known = team.members.length - cost.unknown

  return (
    <li className="card animate-rise p-5 sm:p-6" style={{ animationDelay: `${delayMs}ms` }}>
      <div className="grid gap-6 md:grid-cols-[auto_1fr] md:items-center">
        <div className="flex items-center gap-5 md:flex-col md:gap-2">
          <OddsRing rate={rate} low={low} high={high} delayMs={delayMs + 200} />
          <div className="md:text-center">
            <div className="eyebrow">Team {rank}</div>
            {bestForBudget && (
              <span className="mt-1 inline-block rounded bg-brand px-1.5 py-0.5 text-[10px] font-bold uppercase text-on-brand">
                Best for your budget
              </span>
            )}
            <p className="mt-1 text-xs text-subtle">
              Likely {percent(low)}–{percent(high)}%
              <br />
              {battles} simulated battles
            </p>
          </div>
        </div>

        <div>
          <ul className="grid gap-3 md:grid-cols-3">
            {team.members.map((member, i) => (
              <MemberCard key={member.index} member={member} role={i} />
            ))}
          </ul>
          {known > 0 && (
            <p className="mt-4 text-sm text-ink-soft">
              Power-up cost:{' '}
              <span className="font-semibold tabular-nums text-ink">{cost.stardust.toLocaleString()}</span> Stardust
              {cost.candy > 0 && <>, {cost.candy.toLocaleString()} Candy</>}
              {cost.xlCandy > 0 && <>, {cost.xlCandy.toLocaleString()} Candy XL</>}
              {cost.unknown > 0 && (
                <span className="text-subtle">
                  {' '}
                  (plus {cost.unknown} without a known level)
                </span>
              )}
              {budget !== null && (
                <span className={cost.stardust <= budget ? 'text-brand' : 'text-subtle'}>
                  {' '}
                  · {cost.stardust <= budget ? 'fits your Stardust' : `${(cost.stardust - budget).toLocaleString()} short`}
                </span>
              )}
            </p>
          )}
          {team.coverage && (
            <p className="mt-3 text-sm text-ink-soft">
              Beats <span className="font-semibold tabular-nums text-ink">{team.coverage.answered}</span> of{' '}
              {team.coverage.metaSize} meta Pokémon
              <span className="text-subtle">
                {' '}
                · coverage <span className="tabular-nums">{Math.round(team.coverage.rating)}</span>
              </span>
            </p>
          )}
          {team.threats?.length ? (
            <div className="mt-2 text-sm">
              <p id={`threats-${rank}`} className="text-subtle">Toughest matchups</p>
              <ul aria-labelledby={`threats-${rank}`} className="mt-1 flex flex-wrap gap-1.5">
                {team.threats.map((threat) => (
                  <li
                    key={threat.opponent}
                    title={`Your best answer, ${nameOf(threat.answer)}, rates ${Math.round(threat.rating)} against it (500 is even)`}
                    className="rounded-md bg-canvas/50 px-2 py-1 text-xs ring-1 ring-line"
                  >
                    <span className="text-ink-soft">{metaName(threat.opponent)}</span>{' '}
                    <span className={`font-semibold tabular-nums ${threat.rating < 500 ? 'text-tier-critical' : 'text-ink'}`}>
                      {Math.round(threat.rating)}
                    </span>
                    <span className="text-subtle"> · {nameOf(threat.answer)}</span>
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <p className="mt-2 text-sm text-subtle">
              Toughest matchups:{' '}
              {team.toughest.map((id, i) => (
                <span key={id}>
                  {i > 0 && ', '}
                  <span className="text-ink-soft">{metaName(id)}</span>
                </span>
              ))}
            </p>
          )}
          <SwapSuggestions suggestions={team.suggestions ?? []} members={team.members} onAdd={onAdd} />
        </div>
      </div>
    </li>
  )
}

/** A team's lineup, by position: who starts, who a switch-out goes to, who's saved for the end. */
const ROLES = [
  { name: 'Lead', hint: 'Starts the battle' },
  { name: 'Safe swap', hint: 'Comes in when you switch out' },
  { name: 'Closer', hint: 'Saved for the end' },
]

function MemberCard({ member, role }: { member: TeamMember; role: number }) {
  const lead = role === 0
  const species = useSpecies()
  const moves = useMoves()
  const option = species.status === 'ready' ? species.byId.get(member.speciesId) : undefined
  const moveName = (id: string) => (moves.status === 'ready' ? moves.byId.get(id)?.name : undefined) ?? id

  return (
    <li className={`group flex gap-3 rounded-xl p-3 ring-1 transition hover:-translate-y-0.5 ${lead ? 'bg-brand/10 ring-brand/60' : 'bg-raised/60 ring-line'}`}>
      <Link to={`/species/${member.speciesId}`} className="h-20 w-20 shrink-0 rounded-lg bg-canvas/60 p-1.5" aria-label={option?.name ?? member.speciesId}>
        <PokemonArt dex={option?.dex} alt="" className="h-full w-full transition group-hover:scale-110" />
      </Link>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="truncate font-semibold">{option?.name ?? member.speciesId}</span>
          {ROLES[role] && (
            <span
              title={ROLES[role].hint}
              className={`rounded px-1.5 py-0.5 text-[10px] font-bold uppercase ${
                lead ? 'bg-brand text-on-brand' : 'bg-raised-strong text-ink-soft ring-1 ring-line-strong'
              }`}
            >
              {ROLES[role].name}
            </span>
          )}
          {member.shadow && (
            <span className="rounded bg-violet-500/25 px-1.5 py-0.5 text-[10px] font-bold uppercase text-violet-200">Shadow</span>
          )}
          {member.assumed && (
            <span className="rounded bg-raised-strong px-1.5 py-0.5 text-[10px] font-bold uppercase text-ink-soft ring-1 ring-line-strong">
              Assumed IVs
            </span>
          )}
          {member.evolution && (
            <span className="rounded bg-emerald-500/20 px-1.5 py-0.5 text-[10px] font-bold uppercase text-emerald-300 ring-1 ring-emerald-500/40">
              Evolvable
            </span>
          )}
        </div>
        {member.evolution && <EvolveNote evolution={member.evolution} into={option?.name ?? member.speciesId} />}
        <div className="mt-0.5 text-xs text-subtle">
          Lv <span className="tabular-nums text-ink-soft">{formatLevel(member.level)}</span> ·{' '}
          <span className="tabular-nums text-ink-soft">{member.cp.toLocaleString()}</span> CP
        </div>
        <div className="mt-2 text-sm">
          <IvTriple atk={member.atkIv} def={member.defIv} sta={member.staIv} />
        </div>
        <p className="mt-2 text-xs leading-relaxed text-ink-soft">
          {moveName(member.fastMove)}
          <span className="text-subtle"> · </span>
          {member.chargedMoves.map(moveName).join(' / ')}
        </p>
        {member.cost && (member.cost.stardust > 0 ? (
          <p className="mt-2 text-xs text-subtle">
            To Lv {formatLevel(member.level)}:{' '}
            <span className="tabular-nums text-ink-soft">{member.cost.stardust.toLocaleString()}</span> dust
            {member.cost.candy > 0 && <>, {member.cost.candy} candy</>}
            {member.cost.xlCandy > 0 && <>, {member.cost.xlCandy} XL</>}
            {member.shadow && <span title="Shadow costs are 1.2× and rounded up each power-up, our estimate"> (est.)</span>}
          </p>
        ) : (
          <p className="mt-2 text-xs text-subtle">Already at its best level.</p>
        ))}
        {member.powerUp && <PowerUp impact={member.powerUp} toLevel={member.level} />}
        {member.evolution && (
          <BuildCostDrawer
            member={member}
            name={(id) => (species.status === 'ready' ? species.byId.get(id)?.name : undefined) ?? id}
          />
        )}
      </div>
    </li>
  )
}

/** An evolved member (#138): which of your Pokémon to evolve, and any exclusive move its best moveset needs. */
function EvolveNote({ evolution, into }: { evolution: Evolution; into: string }) {
  const species = useSpecies()
  const from = (species.status === 'ready' ? species.byId.get(evolution.fromSpeciesId)?.name : undefined) ?? evolution.fromSpeciesId
  return (
    <div className="mt-1 text-xs">
      <p className="text-emerald-300">
        Evolve your {from} <span aria-hidden>→</span> {into}
      </p>
      {evolution.moveWarnings.map((warning) => (
        <p key={warning} className="mt-1 leading-relaxed text-amber-300">
          {warning}
        </p>
      ))}
    </div>
  )
}

/** What powering this one up from its current level gets: meta battles won before and after, and which flip. */
function PowerUp({ impact, toLevel }: { impact: PowerUpImpact; toLevel: number }) {
  const metaName = useMetaName()
  const change = impact.winsAfter - impact.winsBefore
  const groups = (list: MatchupRef[]) =>
    groupByOpponent(list).map((g) => (
      <li key={g.opponent}>
        {metaName(g.opponent)} <span className="text-subtle">({shieldsList(g.shields)})</span>
      </li>
    ))
  return (
    <details className="mt-3 rounded-lg bg-canvas/50 px-2.5 py-2 text-xs ring-1 ring-line">
      <summary className="cursor-pointer text-ink-soft">
        Yours is Lv {formatLevel(impact.fromLevel)} ({impact.fromCp.toLocaleString()} CP). Powering to {formatLevel(toLevel)}:{' '}
        <span className={`font-semibold ${change > 0 ? 'text-brand' : 'text-ink'}`}>
          {change > 0 ? `+${change}` : change} wins
        </span>
      </summary>
      <p className="mt-2 text-subtle tabular-nums">
        {impact.winsBefore} → {impact.winsAfter} of {impact.battles} meta battles
      </p>
      {impact.gained.length > 0 && (
        <>
          <p className="mt-2 font-semibold text-ink">Starts beating</p>
          <ul className="mt-1 space-y-0.5 text-ink-soft">{groups(impact.gained)}</ul>
        </>
      )}
      {impact.lost.length > 0 && (
        <>
          <p className="mt-2 font-semibold text-ink">Stops beating</p>
          <ul className="mt-1 space-y-0.5 text-ink-soft">{groups(impact.lost)}</ul>
        </>
      )}
    </details>
  )
}

function HowItWorks() {
  return (
    <details className="card mt-14 max-w-3xl p-5">
      <summary className="cursor-pointer font-semibold">How the Win Odds Work</summary>
      <div className="mt-3 grid gap-3 text-sm leading-relaxed text-ink-soft">
        <p>
          Each Pokémon is built to the highest level that fits the league (at most 50, never below its current level), then
          battled 1v1 against every Pokémon in PvPoke's meta for that league to find your strongest ten.
        </p>
        <p>
          For a Cup, only the Pokémon its rules allow can play, and the opponents come from PvPoke's meta for that Cup. The
          list of Cups, their rules and their metas are refreshed from PvPoke every night.
        </p>
        <p>
          Pokémon ticked on your checklist have no IVs yet, so each gets a good spread for the league: the line where the top 10%
          of all 4,096 IV spreads begins, by stat product. Its moves are PvPoke's recommended ones. Any you've added with exact
          IVs use those instead.
        </p>
        <p>
          Teams of those ten are ranked by how well they cover the meta together. The best few then play full 3v3 battles, with
          switching and shared shields, against 100 randomly drawn meta teams. Each one is tried in all six lineups: a lead, a safe
          swap that comes in whenever you switch out, and a closer saved for the end. After a faint, whoever handles their
          Pokémon best comes in. The best lineup is kept, and the cards show it in order.
        </p>
        <p>
          The odds shown come from a fresh set of 400 battles, separate from the ones used to pick the teams, so a lucky streak
          can't inflate them. The band is a 95% interval: the true odds against this meta are very likely inside it.
        </p>
        <p className="text-subtle">
          The opponent is a simulated player that always makes the same choice in the same position. Real players vary, so treat
          the odds as a comparison between teams, not a promise.
        </p>
      </div>
    </details>
  )
}

import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router'
import { buildTeams, LOGIN_URL, previewImport } from '../api'
import { useCollection } from '../collection/collection-context'
import type { CaughtPokemonRequest, TeamBuilderLeague, TeamBuilderResult, TeamMember, TeamResult } from '../types'
import { useSpecies } from '../species/species-context'
import { useMoves } from '../moves/moves-context'
import { PokemonArt } from '../components/PokemonArt'
import { IvTriple } from '../components/IvTriple'
import { formatLevel } from '../lib/species'

const LEAGUES: { key: TeamBuilderLeague; label: string; cpLabel: string }[] = [
  { key: 'GREAT', label: 'Great', cpLabel: '1500 CP' },
  { key: 'ULTRA', label: 'Ultra', cpLabel: '2500 CP' },
  { key: 'MASTER', label: 'Master', cpLabel: 'No cap' },
]

// Where the Pokémon come from: the saved Collection, or a Poké Genie / Calcy IV file read in the browser session only
type Source =
  | { kind: 'collection'; pokemon: CaughtPokemonRequest[]; withoutMoves: number }
  | { kind: 'file'; fileName: string; pokemon: CaughtPokemonRequest[]; withoutMoves: number }

type CollectionState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; inBrowser: boolean; source: Source }

type Run =
  | { status: 'idle' }
  | { status: 'running'; startedAt: number }
  | { status: 'error'; message: string }
  | { status: 'done'; result: TeamBuilderResult; source: Source }

export function TeamBuilderPage() {
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
      source: { kind: 'collection', pokemon, withoutMoves: saved.list.length - withMoves.length },
    }
  }, [saved])
  const [fileSource, setFileSource] = useState<Source | null>(null)
  const [useFile, setUseFile] = useState(false)
  const [league, setLeague] = useState<TeamBuilderLeague>('GREAT')
  const [run, setRun] = useState<Run>({ status: 'idle' })
  const abort = useRef<AbortController | null>(null)

  useEffect(() => () => abort.current?.abort(), [])

  const source: Source | null =
    useFile || collection.status !== 'ready' ? fileSource : collection.source

  async function build() {
    if (!source || source.pokemon.length === 0) return
    abort.current?.abort()
    const controller = new AbortController()
    abort.current = controller
    setRun({ status: 'running', startedAt: Date.now() })
    try {
      const result = await buildTeams({ league, pokemon: source.pokemon }, controller.signal)
      setRun({ status: 'done', result, source })
    } catch (error: unknown) {
      if (controller.signal.aborted) return
      setRun({ status: 'error', message: error instanceof Error ? error.message : String(error) })
    }
  }

  function changeLeague(next: TeamBuilderLeague) {
    abort.current?.abort()
    setLeague(next)
    setRun({ status: 'idle' })
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <h1 className="text-4xl font-black tracking-tight">Team Builder</h1>
      <p className="mt-2 max-w-2xl text-ink-soft">
        Finds the best three from your Pokémon by battling full 3v3s against teams from the current meta, and tells you how often
        each team wins.
      </p>

      <section className="mt-8 grid gap-6 lg:grid-cols-[1fr_auto] lg:items-end">
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

        <div>
          <div className="text-xs uppercase tracking-wider text-muted">League</div>
          <div role="tablist" aria-label="League" className="mt-2 inline-flex rounded-lg bg-surface p-1 ring-1 ring-line">
            {LEAGUES.map((l) => (
              <button
                key={l.key}
                role="tab"
                aria-selected={l.key === league}
                onClick={() => changeLeague(l.key)}
                className={`rounded-md px-3 py-2 text-left text-sm transition sm:px-4 ${
                  l.key === league ? 'bg-brand text-on-brand' : 'text-ink-soft hover:bg-raised'
                }`}
              >
                <div className="font-bold">{l.label}</div>
                <div className={`text-xs ${l.key === league ? 'text-on-brand/70' : 'text-subtle'}`}>{l.cpLabel}</div>
              </button>
            ))}
          </div>
        </div>
      </section>

      <div className="mt-6 flex flex-wrap items-center gap-4">
        <button
          onClick={build}
          disabled={!source || source.pokemon.length < 3 || run.status === 'running'}
          className="rounded-md bg-brand px-6 py-3 font-bold text-on-brand transition hover:brightness-110 disabled:opacity-50"
        >
          {run.status === 'running' ? 'Building…' : 'Build teams'}
        </button>
        {run.status === 'running' && <Progress startedAt={run.startedAt} />}
        {source?.kind === 'file' && source.pokemon.length > 0 && source.pokemon.length < 3 && (
          <span className="text-sm text-subtle">You need at least 3 Pokémon with moves.</span>
        )}
      </div>

      {run.status === 'error' && (
        <div role="alert" className="mt-6 max-w-2xl rounded-lg border border-red-900 bg-red-950/50 p-5 text-red-200">
          <p className="font-semibold">Couldn't build teams</p>
          <p className="mt-1 text-sm text-red-300/80">{run.message}</p>
        </div>
      )}

      {run.status === 'done' && <Results result={run.result} source={run.source} />}

      <HowItWorks />
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
      const ready = preview.pokemon.filter((p) => !p.needsMoves)
      onFile({
        kind: 'file',
        fileName: file.name,
        pokemon: ready.map(({ speciesId, atkIv, defIv, staIv, level, shadow, fastMove, chargedMoves }) => ({
          speciesId, atkIv, defIv, staIv, level, shadow, fastMove: fastMove ?? '', chargedMoves,
        })),
        withoutMoves: preview.pokemon.length - ready.length,
      })
    } catch (error: unknown) {
      setFileError(error instanceof Error ? error.message : String(error))
    } finally {
      setReading(false)
    }
  }

  const fileInput = (
    <label className={`inline-flex cursor-pointer items-center rounded-md bg-raised px-4 py-2 text-sm font-semibold text-ink ring-1 ring-line-strong transition hover:bg-raised-strong ${reading ? 'pointer-events-none opacity-60' : ''}`}>
      {reading ? 'Reading…' : fileSource ? 'Choose another file' : 'Choose a Poké Genie or Calcy IV export'}
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
    <div className="rounded-lg bg-surface p-5 ring-1 ring-line">
      <div className="text-xs uppercase tracking-wider text-muted">Your Pokémon</div>

      {collection.status === 'loading' && <div className="mt-3 h-6 w-64 animate-pulse rounded bg-raised" />}

      {collection.status === 'ready' && (
        <div className="mt-3 flex flex-wrap gap-2">
          <Choice active={!useFile} onClick={() => onUseFile(false)}>
            My Collection <span className="text-subtle">({collection.source.pokemon.length.toLocaleString()})</span>
          </Choice>
          <Choice active={useFile} onClick={() => onUseFile(true)}>
            An export file
          </Choice>
        </div>
      )}

      {collection.status === 'error' && (
        <p className="mt-2 text-sm text-ink-soft">Couldn't load your Collection ({collection.message}). You can still use a file.</p>
      )}

      {(useFile || collection.status === 'error') && (
        <div className="mt-3 flex flex-wrap items-center gap-3">
          {fileInput}
          {fileSource?.kind === 'file' && (
            <span className="text-sm text-ink-soft">
              {fileSource.pokemon.length.toLocaleString()} Pokémon from <span className="text-ink">{fileSource.fileName}</span>
            </span>
          )}
        </div>
      )}

      {fileError && <p className="mt-2 text-sm text-red-400">{fileError}</p>}

      <SourceNote source={useFile || collection.status !== 'ready' ? fileSource : collection.source} />

      {collection.status === 'ready' && !useFile && collection.source.pokemon.length < 3 && (
        <p className="mt-3 text-sm text-ink-soft">
          {collection.source.pokemon.length === 0 ? 'No Pokémon yet.' : `${collection.source.pokemon.length} so far; a team needs 3.`}{' '}
          <Link to="/collection" className="text-brand underline">
            Add them by name, IVs and CP
          </Link>
          , it takes a few seconds each.
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
      {useFile && <p className="mt-2 text-xs text-subtle">Nothing from the file is saved.</p>}
    </div>
  )
}

function SourceNote({ source }: { source: Source | null }) {
  if (!source || source.withoutMoves === 0) return null
  return (
    <p className="mt-2 text-xs text-subtle">
      {source.withoutMoves.toLocaleString()} without moves are left out
      {source.kind === 'file' ? ' (pick their moves on the Import page to include them).' : '.'}
    </p>
  )
}

function Choice({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      aria-pressed={active}
      className={`rounded-md px-3 py-1.5 text-sm font-semibold ring-1 transition ${
        active ? 'bg-brand/15 text-brand ring-brand/60' : 'bg-raised text-ink-soft ring-line hover:text-ink'
      }`}
    >
      {children}
    </button>
  )
}

function Progress({ startedAt }: { startedAt: number }) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 500)
    return () => clearInterval(id)
  }, [])
  const seconds = Math.floor((now - startedAt) / 1000)
  return (
    <span role="status" className="text-sm text-ink-soft">
      Simulating a few thousand battles… <span className="tabular-nums text-subtle">{seconds}s</span>
    </span>
  )
}

const LEAGUE_NAMES: Record<TeamBuilderLeague, string> = { GREAT: 'Great League', ULTRA: 'Ultra League', MASTER: 'Master League' }

function Results({ result, source }: { result: TeamBuilderResult; source: Source }) {
  const species = useSpecies()
  const nameOf = (id: string) => (species.status === 'ready' ? species.byId.get(id)?.name : undefined) ?? id

  return (
    <section className="mt-10" aria-live="polite">
      {result.teams.length === 0 ? (
        <div className="max-w-2xl rounded-lg bg-surface p-5 ring-1 ring-line">
          <p className="font-semibold">Not enough Pokémon for {LEAGUE_NAMES[result.league]}</p>
          <p className="mt-1 text-sm text-ink-soft">
            A team needs three different species that fit the league. {result.eligible} of yours can play there.
          </p>
        </div>
      ) : (
        <>
          <h2 className="flex flex-wrap items-baseline gap-x-3 text-2xl font-bold">
            <span className="h-7 w-1 self-center rounded bg-brand" aria-hidden="true" />
            Best teams for {LEAGUE_NAMES[result.league]}
            <span className="text-sm font-normal text-subtle">
              from {result.eligible.toLocaleString()} eligible · vs {result.metaSize} meta Pokémon (PvPoke, {result.metaDate})
            </span>
          </h2>
          <ol className="mt-5 grid gap-5">
            {result.teams.map((team, i) => (
              <TeamCard key={team.members.map((m) => m.index).join('-')} rank={i + 1} team={team} />
            ))}
          </ol>
        </>
      )}

      {result.skipped.length > 0 && (
        <details className="mt-6 max-w-2xl rounded-lg bg-surface p-4 ring-1 ring-line">
          <summary className="cursor-pointer text-sm font-semibold">
            {result.skipped.length.toLocaleString()} Pokémon couldn't play in {LEAGUE_NAMES[result.league]}
          </summary>
          <ul className="mt-3 max-h-72 overflow-y-auto text-sm">
            {result.skipped.map((s) => (
              <li key={s.index} className="flex gap-3 border-t border-line/60 py-1.5">
                <span className="w-40 shrink-0 truncate text-ink-soft">{nameOf(source.pokemon[s.index]?.speciesId ?? '')}</span>
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

function TeamCard({ rank, team }: { rank: number; team: TeamResult }) {
  const species = useSpecies()
  const { rate, low, high, battles } = team.winRate

  // PvPoke meta IDs mark Shadows with a suffix; our species list has the base form
  const metaName = (id: string) => {
    const shadow = id.endsWith('_shadow')
    const base = shadow ? id.slice(0, -'_shadow'.length) : id
    const name = (species.status === 'ready' ? species.byId.get(base)?.name : undefined) ?? base
    return shadow ? `Shadow ${name}` : name
  }

  return (
    <li className="rounded-xl bg-surface p-5 ring-1 ring-line">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div className="text-xs uppercase tracking-wider text-muted">Team {rank}</div>
          <div className="mt-1 flex items-baseline gap-2">
            <span className="text-5xl font-black tabular-nums">{percent(rate)}%</span>
            <span className="text-ink-soft">win odds</span>
          </div>
        </div>
        <div className="w-full max-w-xs">
          <OddsBar rate={rate} low={low} high={high} />
          <p className="mt-1 text-xs text-subtle">
            Likely {percent(low)}–{percent(high)}% · {battles} simulated battles
          </p>
        </div>
      </div>

      <ul className="mt-5 grid gap-3 md:grid-cols-3">
        {team.members.map((member, i) => (
          <MemberCard key={member.index} member={member} lead={i === 0} />
        ))}
      </ul>

      <p className="mt-4 text-sm text-subtle">
        Toughest matchups:{' '}
        {team.toughest.map((id, i) => (
          <span key={id}>
            {i > 0 && ', '}
            <span className="text-ink-soft">{metaName(id)}</span>
          </span>
        ))}
      </p>
    </li>
  )
}

/** The estimate's 95% interval as a band on 0–100%, the point estimate as a tick, 50% marked. */
function OddsBar({ rate, low, high }: { rate: number; low: number; high: number }) {
  return (
    <div
      className="relative h-3 rounded-full bg-raised"
      role="img"
      aria-label={`Win odds ${percent(rate)}%, likely between ${percent(low)} and ${percent(high)}%`}
    >
      <div className="absolute inset-y-0 left-1/2 w-px bg-line-strong" aria-hidden="true" />
      <div className="absolute inset-y-0 rounded-full bg-brand/35" style={{ left: `${low * 100}%`, width: `${(high - low) * 100}%` }} />
      <div className="absolute -inset-y-0.5 w-1 -translate-x-1/2 rounded-full bg-brand" style={{ left: `${rate * 100}%` }} />
    </div>
  )
}

function MemberCard({ member, lead }: { member: TeamMember; lead: boolean }) {
  const species = useSpecies()
  const moves = useMoves()
  const option = species.status === 'ready' ? species.byId.get(member.speciesId) : undefined
  const moveName = (id: string) => (moves.status === 'ready' ? moves.byId.get(id)?.name : undefined) ?? id

  return (
    <li className={`flex gap-3 rounded-lg p-3 ring-1 ${lead ? 'bg-brand/5 ring-brand/50' : 'bg-raised/40 ring-line'}`}>
      <Link to={`/species/${member.speciesId}`} className="h-16 w-16 shrink-0 rounded-md bg-raised/60 p-1.5" aria-label={option?.name ?? member.speciesId}>
        <PokemonArt dex={option?.dex} alt="" className="h-full w-full" />
      </Link>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="truncate font-semibold">{option?.name ?? member.speciesId}</span>
          {lead && <span className="rounded bg-brand px-1.5 py-0.5 text-[10px] font-bold uppercase text-on-brand">Lead</span>}
          {member.shadow && (
            <span className="rounded bg-violet-500/20 px-1.5 py-0.5 text-[10px] font-bold uppercase text-violet-300">Shadow</span>
          )}
        </div>
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
      </div>
    </li>
  )
}

function HowItWorks() {
  return (
    <details className="mt-12 max-w-3xl rounded-lg bg-surface p-5 ring-1 ring-line">
      <summary className="cursor-pointer font-semibold">How the win odds work</summary>
      <div className="mt-3 grid gap-3 text-sm leading-relaxed text-ink-soft">
        <p>
          Each Pokémon is built to the highest level that fits the league (at most 50, never below its current level), then
          battled 1v1 against every Pokémon in PvPoke's meta for that league to find your strongest ten.
        </p>
        <p>
          Teams of those ten are ranked by how well they cover the meta together. The best few then play full 3v3 battles, with
          switching and shared shields, against randomly drawn meta teams. Each one is tried with every lead, and the best lead
          is kept.
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

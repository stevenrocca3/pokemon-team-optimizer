import { Link } from 'react-router'
import type { AnalyticsRank, AnalyticsTrend, BattleRow, PlayerAnalytics, TeamRanking } from '../types'
import { jobIdFor, one, signed } from './analytics'

const TIER_TEXT: Record<NonNullable<AnalyticsRank['tier']>['key'], string> = {
  legend: 'text-tier-legend',
  veteran: 'text-tier-veteran',
  ace: 'text-tier-ace',
  critical: 'text-tier-critical',
}

/** A logged-in player's analytics (#124): rank, trend, top teams and the battles behind them. */
export function AnalyticsDashboard({ summary }: { summary: PlayerAnalytics }) {
  if (summary.battles === 0) {
    return (
      <div className="card px-6 py-12 text-center">
        <p className="text-ink-soft">
          No battles saved yet. Every battle you analyse from now on builds your history, your trend and your rank.
        </p>
        <Link to="/analyze" className="btn btn-primary mt-5">
          Analyze a battle
        </Link>
      </div>
    )
  }
  return (
    <div className="space-y-6">
      <div className="grid gap-6 md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        <RankCard rank={summary.rank} />
        <TrendCard trend={summary.trend} />
      </div>
      <TopTeams teams={summary.topTeams} />
      <BattleHistory battles={summary.history} total={summary.battles} />
    </div>
  )
}

/** The player's rank: the last 25 battles' average and its tier (#125 locks it under 10 battles). */
export function RankCard({ rank }: { rank: AnalyticsRank }) {
  return (
    <section aria-labelledby="rank-heading" className="card animate-rise p-6">
      <h2 id="rank-heading" className="font-display text-lg text-ink">
        Your rank
      </h2>
      {rank.locked || rank.score === undefined || !rank.tier ? (
        <p className="mt-3 text-sm text-ink-soft">
          {rank.battles} / {rank.required} battles analyzed
        </p>
      ) : (
        <div className="mt-4">
          <p className="font-mono text-5xl font-black text-ink">{one(rank.score)}</p>
          <p className={`mt-1 font-semibold ${TIER_TEXT[rank.tier.key]}`}>{rank.tier.name}</p>
          <p className="mt-2 text-xs text-muted">Average of your last {Math.min(25, rank.battles)} battles</p>
        </div>
      )}
    </section>
  )
}

const DIRECTION: Record<Exclude<AnalyticsTrend['direction'], 'NOT_ENOUGH_DATA'>, { label: string; tone: string }> = {
  IMPROVING: { label: 'Improving', tone: 'text-trend-up' },
  STABLE: { label: 'Stable', tone: 'text-ink-soft' },
  DECLINING: { label: 'Declining', tone: 'text-tier-critical' },
}

/** Improving, stable or declining (spec §2.1): a vector indicator, the velocity, rolling averages and the why. */
function TrendCard({ trend }: { trend: AnalyticsTrend }) {
  const known = trend.direction === 'NOT_ENOUGH_DATA' ? null : DIRECTION[trend.direction]
  return (
    <section
      aria-labelledby="trend-heading"
      className={`card animate-rise p-6 ${trend.direction === 'DECLINING' ? 'ring-tier-critical/40' : ''}`}
    >
      <h2 id="trend-heading" className="font-display text-lg text-ink">
        Your trend
      </h2>
      {known && trend.velocity !== null && (
        <div className="mt-4 flex items-center gap-4">
          <TrendVector direction={trend.direction as keyof typeof DIRECTION} className={known.tone} />
          <div>
            <p className={`text-xl font-bold ${known.tone}`}>{known.label}</p>
            <p className="text-sm text-muted">
              <span className={`font-mono font-semibold ${known.tone}`}>{signed(trend.velocity)}</span> points, last 5
              battles against the ones before
            </p>
          </div>
        </div>
      )}
      <p className="mt-4 text-sm text-ink-soft">{trend.summary}</p>
      <dl className="mt-5 grid grid-cols-3 gap-3 text-center">
        {(
          [
            ['Last 5', trend.rolling.last5],
            ['Last 10', trend.rolling.last10],
            ['Last 25', trend.rolling.last25],
          ] as const
        ).map(([label, value]) => (
          <div key={label} className="rounded-lg bg-raised/60 px-2 py-2.5">
            <dt className="text-xs text-muted">{label}</dt>
            <dd className="font-mono text-lg font-bold text-ink">{value === null ? '–' : one(value)}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

/** An arrow that draws itself: up and green when improving, down in alert red when declining, level when stable. */
function TrendVector({ direction, className }: { direction: keyof typeof DIRECTION; className: string }) {
  const path = {
    IMPROVING: 'M6 34 L18 22 L26 28 L42 10',
    STABLE: 'M6 24 L42 24',
    DECLINING: 'M6 10 L18 22 L26 16 L42 34',
  }[direction]
  const head = { IMPROVING: 'M32 10 L42 10 L42 20', STABLE: 'M34 17 L42 24 L34 31', DECLINING: 'M42 24 L42 34 L32 34' }[
    direction
  ]
  return (
    <svg viewBox="0 0 48 44" aria-hidden data-direction={direction} className={`size-14 shrink-0 ${className}`}>
      <path
        d={path}
        pathLength={1}
        className="trend-draw"
        fill="none"
        stroke="currentColor"
        strokeWidth="3.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d={head}
        pathLength={1}
        className="trend-draw [animation-delay:450ms]"
        fill="none"
        stroke="currentColor"
        strokeWidth="3.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/** The player's three best teams (spec §2.2), best first. */
function TopTeams({ teams }: { teams: TeamRanking[] }) {
  return (
    <section aria-labelledby="teams-heading" className="card animate-rise p-6">
      <h2 id="teams-heading" className="font-display text-lg text-ink">
        Your top teams
      </h2>
      {teams.length === 0 ? (
        <p className="mt-3 text-sm text-muted">A team shows up here once you’ve battled with it 3 times.</p>
      ) : (
        <ol aria-label="Your top teams" className="mt-4 grid gap-3 md:grid-cols-3">
          {teams.map((t, i) => (
            <li key={t.team.join('/')} className="rounded-xl bg-raised/60 p-4 ring-1 ring-line">
              <p className="flex items-baseline justify-between gap-2">
                <span className="eyebrow text-brand">#{i + 1}</span>
                <span className="font-mono text-xl font-bold text-ink" title="Rank score">
                  {one(t.rankScore)}
                </span>
              </p>
              <p className="mt-1 font-semibold text-ink">{t.team.join(' / ')}</p>
              <p className="mt-2 text-xs text-muted">
                {t.battles} battles · {t.wins}W {t.losses}L
              </p>
              <p className="text-xs text-muted">
                Avg score {one(t.averageScore)} · Win rate {one(t.winRatePct)}%
              </p>
            </li>
          ))}
        </ol>
      )}
      <p className="mt-3 text-xs text-subtle">
        Rank score: average execution 60%, win rate 40%. Win rate is estimated, since recordings that stop early have no
        result.
      </p>
    </section>
  )
}

const OUTCOME: Record<'WIN' | 'LOSS', string> = { WIN: 'Win', LOSS: 'Loss' }

function BattleHistory({ battles, total }: { battles: BattleRow[]; total: number }) {
  return (
    <section aria-labelledby="history-heading" className="animate-rise">
      <h2 id="history-heading" className="font-display text-lg text-ink">
        Your battles{' '}
        <span className="text-sm text-muted">
          ({total > battles.length ? `newest ${battles.length} of ${total}` : total})
        </span>
      </h2>
      <ul aria-label="Your battles" className="mt-3 space-y-2">
        {battles.map((b) => (
          <li key={b.matchId}>
            <Link
              to={`/match/${jobIdFor(b.matchId)}`}
              className="card flex items-center gap-4 px-5 py-3 transition hover:ring-brand"
            >
              <span className="min-w-0 flex-1">
                <span className="block truncate font-semibold text-ink">
                  {b.userTeam.join(' / ')} <span className="font-normal text-muted">vs</span> {b.oppTeam.join(' / ')}
                </span>
                <span className="block text-sm text-muted">
                  {b.outcome ? OUTCOME[b.outcome] : 'Result not recorded'} ·{' '}
                  {new Date(b.createdAt).toLocaleDateString()}
                </span>
              </span>
              <span className="font-mono text-lg font-bold text-brand">{Math.round(b.score)}</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

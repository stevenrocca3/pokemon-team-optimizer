import { useEffect, useId, useRef, useState } from 'react'
import type { AnalysisResult, Finding, TurnLogEntry } from '../types'
import { CATEGORY_LABEL, formatClock, matchups, percent, share, timelineSpan, trackPath } from './timeline'

const W = 1000
const H = 100

interface Track {
  name: string
  pick: (e: TurnLogEntry) => number | null
  side: 'user' | 'opp'
  kind: 'hp' | 'energy'
}

const TRACKS: Track[] = [
  { name: 'Your HP', pick: (e) => e.user_hp_pct, side: 'user', kind: 'hp' },
  { name: 'Opponent HP', pick: (e) => e.opp_hp_pct, side: 'opp', kind: 'hp' },
  { name: 'Your energy', pick: (e) => e.user_energy, side: 'user', kind: 'energy' },
  { name: 'Opponent energy', pick: (e) => e.opp_energy, side: 'opp', kind: 'energy' },
]

/**
 * The battle from 00:00 to the end: HP and energy for both sides, each Pokémon named where it came in, and a red
 * flag at every mistake. A flag explains itself on hover or focus; a click (or tap) pins the explanation open.
 */
export function MatchTimeline({ result }: { result: AnalysisResult }) {
  const entries = result.turn_log.turn_log
  const span = timelineSpan(result.turn_log)
  const findings = result.report.findings
  const [hovered, setHovered] = useState<number | null>(null)
  const [focused, setFocused] = useState<number | null>(null)
  const [pinned, setPinned] = useState<number | null>(null)
  const shown = pinned ?? hovered ?? focused
  const root = useRef<HTMLElement>(null)
  const tipId = useId()

  // a pinned explanation closes on a tap anywhere else
  useEffect(() => {
    if (pinned === null) return
    const away = (e: PointerEvent) => {
      if (!(e.target as HTMLElement).closest?.('[data-flag]')) setPinned(null)
    }
    document.addEventListener('pointerdown', away)
    return () => document.removeEventListener('pointerdown', away)
  }, [pinned])

  const ticks = [0, 0.25, 0.5, 0.75, 1].map((s) => formatClock(s * (span.end - span.start)))

  return (
    <figure
      ref={root}
      className="card p-4 sm:p-6"
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          setPinned(null)
          setHovered(null)
          setFocused(null)
        }
      }}
    >
      <figcaption className="flex flex-wrap items-baseline justify-between gap-2">
        <span className="font-display text-xl text-ink">Match timeline</span>
        <span className="flex items-center gap-4 text-xs text-muted">
          <Legend className="bg-brand" label="You" />
          <Legend className="bg-ink-soft" label="Opponent" />
          <span className="flex items-center gap-1.5">
            <FlagIcon className="size-3.5 text-danger" /> Mistake
          </span>
        </span>
      </figcaption>

      <div className="mt-5 grid grid-cols-[5.5rem_1fr] gap-x-3 sm:grid-cols-[7rem_1fr]">
        {/* the flag lane */}
        <span className="self-center text-xs text-muted">{findings.length ? 'Mistakes' : 'No mistakes'}</span>
        <div className="relative h-9">
          {findings.map((f, i) => (
            <FlagButton
              key={`${f.turn}-${f.category}-${i}`}
              finding={f}
              left={share(f.timestamp_ms, span)}
              start={span.start}
              open={shown === i}
              tipId={tipId}
              onHover={(on) => setHovered(on ? i : null)}
              onFocus={(on) => setFocused(on ? i : null)}
              onToggle={() => setPinned(pinned === i ? null : i)}
            />
          ))}
        </div>

        {TRACKS.map((track) => {
          const path = trackPath(entries, track.pick, span, W, H)
          const tall = track.kind === 'hp'
          return (
            <TrackRow
              key={track.name}
              label={track.name}
              tall={tall}
              names={
                tall &&
                matchups(entries, track.side, span).map((m) => (
                  <span
                    key={`${m.species}-${m.at}`}
                    data-row={m.row}
                    className={`absolute border-l border-line-strong pl-1 text-[0.65rem] leading-3 whitespace-nowrap text-muted ${m.row ? 'top-3' : 'top-0'}`}
                    style={{ left: percent(m.at) }}
                  >
                    {m.species}
                  </span>
                ))
              }
            >
              <svg
                viewBox={`0 0 ${W} ${H}`}
                preserveAspectRatio="none"
                role="img"
                aria-label={track.name}
                className="absolute inset-0 size-full"
              >
                {[25, 50, 75].map((y) => (
                  <line
                    key={y}
                    x1={0}
                    x2={W}
                    y1={y}
                    y2={y}
                    className="stroke-line"
                    strokeWidth={1}
                    vectorEffect="non-scaling-stroke"
                  />
                ))}
                {findings.map((f, i) => {
                  const x = share(f.timestamp_ms, span) * W
                  return (
                    <line
                      key={i}
                      x1={x}
                      x2={x}
                      y1={0}
                      y2={H}
                      className={`stroke-danger transition-opacity ${shown === i ? 'opacity-90' : 'opacity-35'}`}
                      strokeWidth={shown === i ? 2 : 1}
                      strokeDasharray="3 3"
                      vectorEffect="non-scaling-stroke"
                    />
                  )
                })}
                <path
                  d={path}
                  fill="none"
                  className={track.side === 'user' ? 'stroke-brand' : 'stroke-ink-soft'}
                  strokeWidth={tall ? 2.25 : 1.5}
                  strokeLinejoin="round"
                  vectorEffect="non-scaling-stroke"
                />
              </svg>
              {!path && (
                <span className="absolute inset-0 grid place-items-center text-xs text-subtle">
                  Not shown on screen
                </span>
              )}
            </TrackRow>
          )
        })}

        <span />
        <div data-testid="timeline-axis" className="mt-1.5 flex justify-between font-mono text-[0.65rem] text-subtle">
          {ticks.map((t, i) => (
            <span key={i}>{t}</span>
          ))}
        </div>
      </div>
    </figure>
  )
}

/** one track: its label, then (HP tracks) a strip naming each Pokémon where it came in, then the plot */
function TrackRow({
  label,
  tall,
  names,
  children,
}: {
  label: string
  tall: boolean
  names: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <>
      <span className={`self-center py-1 text-xs text-ink-soft ${tall ? 'pt-7' : ''}`}>{label}</span>
      <div className="my-0.5">
        {tall && <div className="relative h-6 overflow-hidden">{names}</div>}
        <div className={`relative overflow-hidden rounded-md bg-raised/50 ${tall ? 'h-16' : 'h-10'}`}>{children}</div>
      </div>
    </>
  )
}

function FlagButton({
  finding,
  left,
  start,
  open,
  tipId,
  onHover,
  onFocus,
  onToggle,
}: {
  finding: Finding
  left: number
  /** when the battle starts in the video: the explanation tells the time on the battle clock */
  start: number
  open: boolean
  tipId: string
  onHover: (on: boolean) => void
  onFocus: (on: boolean) => void
  onToggle: () => void
}) {
  // near the right edge the explanation opens leftwards, so it stays on screen
  const align = left > 0.65 ? 'right-0' : left < 0.35 ? 'left-0' : 'left-1/2 -translate-x-1/2'
  return (
    <div className={`absolute bottom-0 -translate-x-1/2 ${open ? 'z-30' : 'z-10'}`} style={{ left: percent(left) }}>
      <button
        type="button"
        data-flag
        aria-label={finding.message}
        aria-expanded={open}
        aria-describedby={open ? tipId : undefined}
        onPointerEnter={() => onHover(true)}
        onPointerLeave={() => onHover(false)}
        onFocus={() => onFocus(true)}
        onBlur={() => onFocus(false)}
        onClick={onToggle}
        className={`grid size-8 place-items-center rounded-full transition hover:scale-110 ${open ? 'bg-danger/20' : ''}`}
      >
        <FlagIcon className="size-5 text-danger drop-shadow" />
      </button>
      {open && (
        <div
          id={tipId}
          role="tooltip"
          className={`animate-rise absolute top-full z-20 mt-2 w-72 max-w-[80vw] rounded-xl bg-raised-strong p-3 text-left text-sm shadow-xl ring-1 ring-danger/60 ${align}`}
        >
          <p className="flex items-center justify-between gap-3 text-xs">
            <span className="font-semibold tracking-wide text-danger uppercase">
              {CATEGORY_LABEL[finding.category]}
            </span>
            <span className="font-mono text-muted">
              {formatClock(finding.timestamp_ms - start)} · <span className="text-danger">−{finding.deduction}</span>
            </span>
          </p>
          <p className="mt-1.5 text-ink">{finding.message}</p>
          {finding.confidence === 'low' && (
            <p className="mt-1 text-xs text-subtle">Low-confidence read: check the video.</p>
          )}
        </div>
      )}
    </div>
  )
}

function FlagIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 16 16" aria-hidden className={className}>
      <path d="M3.5 1.5v13" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M4 2h8.5l-2 3 2 3H4z" fill="currentColor" />
    </svg>
  )
}

function Legend({ className, label }: { className: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span aria-hidden className={`h-0.5 w-4 rounded ${className}`} />
      {label}
    </span>
  )
}

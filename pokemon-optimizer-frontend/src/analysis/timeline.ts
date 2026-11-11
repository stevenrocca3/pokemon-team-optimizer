import type { FindingCategory, TurnLog, TurnLogEntry } from '../types'

/** The match's geometry for MatchTimeline: a clock from 00:00, and tracks as SVG paths. */

const pad = (n: number) => String(n).padStart(2, '0')

/** mm:ss, rounded down like a stopwatch */
export function formatClock(ms: number): string {
  const seconds = Math.floor(Math.max(0, ms) / 1000)
  return `${pad(Math.floor(seconds / 60))}:${pad(seconds % 60)}`
}

/** the battle's stretch of the recording, in video milliseconds */
export interface Span {
  start: number
  end: number
}

const TURN_MS = 500

/** from the first turn with the battle HUD up to the end of the last one (recordings often open on the lobby) */
export function timelineSpan(log: TurnLog): Span {
  const battle = log.turn_log.filter((e) => e.hud_visible)
  const turns = battle.length ? battle : log.turn_log
  if (!turns.length) return { start: 0, end: 0 }
  return { start: turns[0].timestamp_ms, end: turns[turns.length - 1].timestamp_ms + TURN_MS }
}

/** how far along the battle (0-1) a moment is */
export function share(ms: number, span: Span): number {
  const length = span.end - span.start
  return length > 0 ? Math.min(1, Math.max(0, (ms - span.start) / length)) : 0
}

/** a share as a CSS percentage, e.g. 0.525 -> "52.5%" */
export const percent = (s: number) => `${+(s * 100).toFixed(2)}%`

const round = (n: number) => +n.toFixed(1)

/**
 * An SVG path for one track: time across `width`, the value (0-100) up `height` with 100 at the top. A turn with no
 * reading lifts the pen, so gaps show as gaps.
 */
export function trackPath(
  entries: TurnLogEntry[],
  pick: (e: TurnLogEntry) => number | null,
  span: Span,
  width: number,
  height: number,
): string {
  const parts: string[] = []
  let drawing = false
  for (const e of entries) {
    if (e.timestamp_ms < span.start || e.timestamp_ms > span.end) continue
    const value = pick(e)
    if (value === null || value === undefined) {
      drawing = false
      continue
    }
    const x = round(share(e.timestamp_ms, span) * width)
    const y = round((1 - Math.min(100, Math.max(0, value)) / 100) * height)
    parts.push(`${drawing ? 'L' : 'M'}${x},${y}`)
    drawing = true
  }
  return parts.join(' ')
}

const LABEL_GAP = 0.08 // names closer than this share of the timeline would overlap

/**
 * Where each Pokémon came in on one side, in order, with the label row to print its name on: a name that would
 * overlap the one before goes on the other row.
 */
export function matchups(entries: TurnLogEntry[], side: 'user' | 'opp', span: Span) {
  const out: { species: string; at: number; row: number }[] = []
  const lastOnRow = [-Infinity, -Infinity]
  for (const e of entries) {
    if (e.timestamp_ms < span.start || e.timestamp_ms > span.end) continue
    const species = side === 'user' ? e.user_active : e.opp_active
    if (!species || species === out.at(-1)?.species) continue
    const at = share(e.timestamp_ms, span)
    const row = at - lastOnRow[0] >= LABEL_GAP ? 0 : at - lastOnRow[1] >= LABEL_GAP ? 1 : 0
    lastOnRow[row] = at
    out.push({ species, at, row })
  }
  return out
}

/** what each kind of finding is called on screen */
export const CATEGORY_LABEL: Record<FindingCategory, string> = {
  timing: 'Fast move timing',
  shield: 'Shield',
  switch: 'Switching',
  charge: 'Charged move',
}

import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import { blipGlow, blips, sweepAngle } from './radar'
import { useAnalysisJob, type ActiveJob } from './analysis-job'

const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

/**
 * What /analyze shows once a video is sent: a radar scope sweeping for contacts (more light up as the analysis gets
 * further) beside a live log of the stages the worker reports. Finished, it links to the breakdown.
 */
export function ScanVisualizer() {
  const { job, clear } = useAnalysisJob()
  if (!job) return null
  const log = job.log ?? [job.message]
  const done = job.status === 'complete' || job.status === 'failed'

  return (
    <section className="card grid items-center gap-6 p-5 sm:grid-cols-[16rem_1fr] sm:p-8">
      <Radar job={job} />
      <div className="min-w-0">
        <p className="eyebrow">{job.fileName}</p>
        <p className="mt-1 font-display text-3xl text-ink">
          {job.status === 'complete'
            ? `${job.score}/100`
            : job.status === 'failed'
              ? 'Couldn’t read it'
              : `${job.progress}%`}
        </p>
        {job.status === 'complete' && job.tier && <p className="text-sm text-ink-soft">{job.tier}</p>}

        <div role="log" aria-label="Analysis log" aria-live="polite" className="mt-4">
          <ol className="space-y-1.5 font-mono text-xs">
            {log.map((line, i) => {
              const current = i === log.length - 1
              const failed = current && job.status === 'failed'
              return (
                <li
                  key={`${i}-${line}`}
                  aria-current={current ? 'step' : undefined}
                  className={`animate-rise flex items-start gap-2 ${current ? (failed ? 'text-ink' : 'text-brand') : 'text-muted'}`}
                >
                  <StepMark kind={failed ? 'failed' : current && !done ? 'current' : 'done'} />
                  <span>
                    {line}
                    {current && !done && (
                      <span aria-hidden className="ml-1 inline-block h-3 w-1.5 animate-pulse bg-brand align-[-2px]" />
                    )}
                  </span>
                </li>
              )
            })}
          </ol>
        </div>

        {done && (
          <div className="mt-5 flex flex-wrap gap-3">
            {job.status === 'complete' && (
              <Link to={`/match/${job.jobId}`} className="btn btn-primary">
                See your {job.score}/100 breakdown
              </Link>
            )}
            <button type="button" onClick={clear} className="btn btn-secondary">
              Analyze another battle
            </button>
          </div>
        )}
      </div>
    </section>
  )
}

/** the mark beside a log line: a tick for a finished stage, an arrow for the current one, a cross for a failure */
function StepMark({ kind }: { kind: 'done' | 'current' | 'failed' }) {
  const path = { done: 'M2 6.5l2.5 2.5L10 3', current: 'M4 2l4 4-4 4', failed: 'M3 3l6 6M9 3l-6 6' }[kind]
  return (
    <svg viewBox="0 0 12 12" aria-hidden className={`mt-0.5 size-3 shrink-0 ${kind === 'failed' ? 'text-danger' : ''}`}>
      <path d={path} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/** The scope itself, on a canvas: rings, the sweeping arm, contacts, and the progress around the rim. */
function Radar({ job }: { job: ActiveJob }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const { progress, status } = job

  useEffect(() => {
    const el = canvas.current
    let ctx: CanvasRenderingContext2D | null = null
    try {
      ctx = el?.getContext('2d') ?? null
    } catch {
      ctx = null // no canvas support (tests): the label still says how far it's got
    }
    if (!el || !ctx) return
    const css = getComputedStyle(document.documentElement)
    const color = (name: string) => css.getPropertyValue(name).trim() || '#e0a458'
    const colors = {
      brand: color('--color-brand'),
      line: color('--color-line'),
      danger: color('--color-danger'),
      surface: color('--color-raised'),
    }
    const dpr = window.devicePixelRatio || 1
    const size = el.clientWidth || 256
    el.width = size * dpr
    el.height = size * dpr
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

    const still = reducedMotion() || status === 'complete' || status === 'failed'
    const started = performance.now()
    let frame = 0
    const draw = (now: number) => {
      drawScope(ctx!, size, still ? Math.PI * 1.75 : sweepAngle(now - started), progress, status, colors)
      if (!still) frame = requestAnimationFrame(draw)
    }
    draw(started)
    return () => cancelAnimationFrame(frame)
  }, [progress, status])

  return (
    <canvas
      ref={canvas}
      role="img"
      aria-label={
        status === 'complete'
          ? 'Scan complete'
          : status === 'failed'
            ? 'Scan failed'
            : `Scanning your battle: ${progress}%`
      }
      className="mx-auto aspect-square w-full max-w-64"
    />
  )
}

type Colors = { brand: string; line: string; danger: string; surface: string }

function drawScope(
  ctx: CanvasRenderingContext2D,
  size: number,
  sweep: number,
  progress: number,
  status: string,
  c: Colors,
) {
  const mid = size / 2
  const r = mid - 10
  const accent = status === 'failed' ? c.danger : c.brand
  // canvas angles start at 3 o'clock; the radar's at 12
  const at = (angle: number) => angle - Math.PI / 2
  ctx.clearRect(0, 0, size, size)

  // the scope face, rings and crosshair
  const face = ctx.createRadialGradient(mid, mid, 0, mid, mid, r)
  face.addColorStop(0, c.surface)
  face.addColorStop(1, 'transparent')
  ctx.fillStyle = face
  ctx.beginPath()
  ctx.arc(mid, mid, r, 0, Math.PI * 2)
  ctx.fill()
  ctx.strokeStyle = c.line
  ctx.lineWidth = 1
  for (const share of [0.25, 0.5, 0.75, 1]) {
    ctx.beginPath()
    ctx.arc(mid, mid, r * share, 0, Math.PI * 2)
    ctx.stroke()
  }
  ctx.beginPath()
  ctx.moveTo(mid - r, mid)
  ctx.lineTo(mid + r, mid)
  ctx.moveTo(mid, mid - r)
  ctx.lineTo(mid, mid + r)
  ctx.stroke()

  // the sweep: a fading wedge trailing the arm
  if (status !== 'failed') {
    const trail = 1.1
    if (typeof ctx.createConicGradient === 'function') {
      const cone = ctx.createConicGradient(at(sweep - trail), mid, mid)
      cone.addColorStop(0, 'transparent')
      cone.addColorStop(trail / (Math.PI * 2), withAlpha(accent, 0.35))
      cone.addColorStop(trail / (Math.PI * 2) + 0.0001, 'transparent')
      ctx.fillStyle = cone
    } else {
      ctx.fillStyle = withAlpha(accent, 0.15)
    }
    ctx.beginPath()
    ctx.moveTo(mid, mid)
    ctx.arc(mid, mid, r, at(sweep - trail), at(sweep))
    ctx.closePath()
    ctx.fill()
    ctx.strokeStyle = accent
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.moveTo(mid, mid)
    ctx.lineTo(mid + Math.cos(at(sweep)) * r, mid + Math.sin(at(sweep)) * r)
    ctx.stroke()
  }

  // contacts
  for (const b of blips(status === 'complete' ? 100 : progress)) {
    const glow = status === 'complete' ? 1 : blipGlow(b.angle, sweep)
    const x = mid + Math.cos(at(b.angle)) * r * b.radius
    const y = mid + Math.sin(at(b.angle)) * r * b.radius
    ctx.fillStyle = withAlpha(accent, glow)
    ctx.shadowColor = accent
    ctx.shadowBlur = 12 * glow
    ctx.beginPath()
    ctx.arc(x, y, 3 + 2 * glow, 0, Math.PI * 2)
    ctx.fill()
  }
  ctx.shadowBlur = 0

  // progress round the rim
  ctx.strokeStyle = accent
  ctx.lineWidth = 4
  ctx.lineCap = 'round'
  ctx.beginPath()
  ctx.arc(mid, mid, r + 5, at(0), at((Math.min(100, status === 'failed' ? 100 : progress) / 100) * Math.PI * 2))
  ctx.stroke()
  ctx.lineCap = 'butt'
}

/** #rrggbb with an alpha */
function withAlpha(hex: string, alpha: number): string {
  const n = parseInt(hex.replace('#', ''), 16)
  if (Number.isNaN(n)) return hex
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha.toFixed(3)})`
}

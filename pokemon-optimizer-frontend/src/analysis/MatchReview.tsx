import { useEffect, useState, type ReactNode } from 'react'
import type { MatchReport } from '../types'

type TierKey = MatchReport['tier']['key']

const TIER_STYLE: Record<TierKey, { badge: string; ring: string }> = {
  legend: { badge: 'bg-tier-legend/15 text-tier-legend ring-tier-legend/60', ring: 'stroke-tier-legend' },
  veteran: { badge: 'bg-tier-veteran/15 text-tier-veteran ring-tier-veteran/60', ring: 'stroke-tier-veteran' },
  ace: { badge: 'bg-tier-ace/15 text-tier-ace ring-tier-ace/60', ring: 'stroke-tier-ace' },
  critical: { badge: 'bg-tier-critical/15 text-tier-critical ring-tier-critical/60', ring: 'stroke-tier-critical' },
}

const DEDUCTION_LABEL: Record<keyof MatchReport['counts'], string> = {
  timing_errors: 'Fast move timing',
  mismanaged_shields: 'Shields',
  suboptimal_switches: 'Switches',
  suboptimal_charges: 'Charged move choice',
}

const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? true

/** counts up from 0 to `target` over ~0.9 s (at once with reduced motion) */
function useCountUp(target: number): number {
  const [shown, setShown] = useState(() => (reducedMotion() ? target : 0))
  useEffect(() => {
    if (reducedMotion()) return
    const start = performance.now()
    let frame = 0
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / 900)
      setShown(Math.round(target * (1 - (1 - t) ** 3)))
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target])
  return shown
}

/** The verdict: the score out of 100 in a ring, the tier as a colour-coded badge, and where the points went. */
export function ScoreHero({ report }: { report: MatchReport }) {
  const shown = useCountUp(report.score)
  const style = TIER_STYLE[report.tier.key]
  const r = 52
  const circumference = 2 * Math.PI * r
  const [filled, setFilled] = useState(false)
  useEffect(() => {
    const frame = requestAnimationFrame(() => setFilled(true))
    return () => cancelAnimationFrame(frame)
  }, [])
  const lost = (Object.keys(DEDUCTION_LABEL) as (keyof MatchReport['counts'])[]).filter((k) => report.counts[k] > 0)

  return (
    <section className="card grid items-center gap-6 p-6 sm:grid-cols-[auto_1fr] sm:p-8">
      <div role="img" aria-label={`Score: ${report.score} out of 100`} className="relative mx-auto size-40">
        <svg viewBox="0 0 120 120" aria-hidden className="size-full -rotate-90">
          <circle cx="60" cy="60" r={r} fill="none" className="stroke-raised" strokeWidth="9" />
          <circle
            cx="60"
            cy="60"
            r={r}
            fill="none"
            className={`${style.ring} transition-[stroke-dashoffset] duration-[900ms] ease-out`}
            strokeWidth="9"
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={filled ? circumference * (1 - report.score / 100) : circumference}
          />
        </svg>
        <span aria-hidden className="absolute inset-0 grid place-content-center text-center">
          <span className="font-display text-5xl leading-none text-ink">{shown}</span>
          <span className="font-mono text-xs text-muted">/100</span>
        </span>
      </div>

      <div className="min-w-0 text-center sm:text-left">
        <p className="eyebrow">Legend benchmark</p>
        <p
          data-tier={report.tier.key}
          className={`mt-2 inline-block rounded-full px-3 py-1 text-sm font-bold ring-1 ${style.badge}`}
        >
          {report.tier.name}
        </p>
        {lost.length > 0 && (
          <ul aria-label="Deductions" className="mt-4 flex flex-wrap justify-center gap-2 sm:justify-start">
            {lost.map((k) => (
              <li
                key={k}
                className="flex items-baseline gap-1.5 rounded-lg bg-raised px-2.5 py-1 text-xs text-ink-soft"
              >
                <span className="font-mono font-bold text-tier-critical">−{report.deductions[k]}</span>
                <span>{DEDUCTION_LABEL[k]}</span>
                <span className="font-mono text-muted">×{report.counts[k]}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

/** The three blunt sections: what lost the match, the shield verdict, and the fast move turn numbers. */
export function CritiqueCards({ report }: { report: MatchReport }) {
  const { summary, metrics, findings } = report
  const wasted = findings.filter((f) => f.category === 'shield').map((f) => f.message)
  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <Card title="Fatal Flaws" tone="critical">
        <Bullets items={summary.fatal_flaws} />
      </Card>
      <Card title="Shield Audit">
        <p className="font-semibold text-ink">{summary.shield_audit}</p>
        {wasted.length > 0 && <Bullets items={wasted} />}
      </Card>
      <Card title="Fast Move Turn Efficiency">
        <Bullets
          mono
          items={[
            `Turns lost to suboptimal timing: ${metrics.free_turns_given ?? 0}`,
            `Off-alignment throws: ${metrics.off_alignment_throws ?? 0} of ${metrics.charged_moves_thrown ?? 0}`,
            `Turns lost to switch lag: ${metrics.switch_lag_turns ?? 0}`,
          ]}
        />
      </Card>
    </div>
  )
}

function Card({ title, tone, children }: { title: string; tone?: 'critical'; children: ReactNode }) {
  const id = `critique-${title.toLowerCase().replace(/\W+/g, '-')}`
  return (
    <section aria-labelledby={id} className={`card p-5 ${tone === 'critical' ? 'ring-tier-critical/40' : ''}`}>
      <h2 id={id} className={`font-display text-lg ${tone === 'critical' ? 'text-tier-critical' : 'text-ink'}`}>
        {title}
      </h2>
      <div className="mt-3 space-y-3 text-sm text-ink-soft">{children}</div>
    </section>
  )
}

function Bullets({ items, mono }: { items: string[]; mono?: boolean }) {
  return (
    <ul className={`space-y-2 ${mono ? 'font-mono text-xs' : ''}`}>
      {items.map((item, i) => (
        <li key={i} className="flex gap-2">
          <span aria-hidden className="mt-1.5 size-1.5 shrink-0 rounded-full bg-tier-critical/80" />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  )
}

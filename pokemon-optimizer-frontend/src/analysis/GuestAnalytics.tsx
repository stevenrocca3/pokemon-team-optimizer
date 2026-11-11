import { LoginCta } from './LoginCta'

/**
 * What a logged-out visitor sees on /analytics (#124, spec §3.1): no player data, just the call to action over a
 * blurred, clearly labelled example of the dashboard they'd get.
 */
export function GuestAnalytics() {
  return (
    <div className="relative">
      <p className="mb-3 inline-block rounded-full bg-raised-strong px-2.5 py-0.5 text-xs font-semibold tracking-wide text-muted uppercase ring-1 ring-line">
        Example
      </p>
      <div className="pointer-events-none select-none" aria-hidden>
        <div className="grid gap-6 opacity-50 blur-[6px] md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
          <div className="card p-6">
            <p className="font-display text-lg text-ink">Your rank</p>
            <p className="mt-4 font-mono text-5xl font-black text-ink">78.6</p>
            <p className="mt-1 font-semibold text-tier-veteran">Veteran / Expert Tier Execution</p>
          </div>
          <div className="card p-6">
            <p className="font-display text-lg text-ink">Your trend</p>
            <p className="mt-4 text-xl font-bold text-trend-up">Improving · +6.2</p>
            <p className="mt-3 text-sm text-ink-soft">
              Biggest gain: timing errors, 0.4 per battle now against 1.2 before.
            </p>
          </div>
          <div className="card p-6 md:col-span-2">
            <p className="font-display text-lg text-ink">Your top teams</p>
            <div className="mt-4 grid gap-3 md:grid-cols-3">
              {[
                'Medicham / Lickitung / Noctowl',
                'Azumarill / Registeel / Trevenant',
                'Swampert / Cradily / Clodsire',
              ].map((team, i) => (
                <div key={team} className="rounded-xl bg-raised/60 p-4">
                  <p className="eyebrow text-brand">#{i + 1}</p>
                  <p className="mt-1 font-semibold text-ink">{team}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
      <div className="absolute inset-0 grid place-items-center p-4">
        <div className="w-full max-w-xl animate-rise">
          <LoginCta />
        </div>
      </div>
    </div>
  )
}

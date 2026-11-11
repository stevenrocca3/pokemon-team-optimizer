import { LOGIN_URL } from '../api'

/**
 * "Login to receive catered battle analytics" (#124, spec §3.1), with a one-click Google login. Battles analysed
 * before logging in aren't lost: they're claimed into the account on login (#120).
 */
export function LoginCta({ compact = false }: { compact?: boolean }) {
  return (
    <section
      aria-labelledby="login-cta"
      className={`card relative overflow-hidden shadow-2xl ring-brand/50 ${compact ? 'p-5 sm:p-6' : 'p-6 text-center sm:p-10'}`}
    >
      <div
        aria-hidden
        className="pointer-events-none absolute -top-24 -right-16 size-64 rounded-full bg-brand/10 blur-3xl"
      />
      <div className={`relative ${compact ? 'sm:flex sm:items-center sm:justify-between sm:gap-6' : ''}`}>
        <div>
          <h2 id="login-cta" className={`font-display text-ink ${compact ? 'text-xl' : 'text-2xl sm:text-3xl'}`}>
            Login to receive catered battle analytics
          </h2>
          <p className={`mt-2 text-sm text-ink-soft ${compact ? '' : 'mx-auto max-w-md'}`}>
            Every battle you analyse goes into your history: whether you’re improving, which of your teams win, and your
            rank once you’ve played 10. Battles from before you log in come with you.
          </p>
        </div>
        <a href={LOGIN_URL} className={`btn btn-primary shrink-0 gap-2 ${compact ? 'mt-4 sm:mt-0' : 'mt-6'}`}>
          <GoogleMark />
          Continue with Google
        </a>
      </div>
    </section>
  )
}

function GoogleMark() {
  return (
    <svg viewBox="0 0 18 18" aria-hidden className="size-4">
      <path
        fill="#4285F4"
        d="M17.6 9.2c0-.6-.1-1.2-.2-1.7H9v3.3h4.8a4.1 4.1 0 0 1-1.8 2.7v2.2h2.9c1.7-1.6 2.7-3.9 2.7-6.5z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.4 0 4.5-.8 6-2.2l-2.9-2.2c-.8.5-1.8.9-3.1.9-2.4 0-4.4-1.6-5.1-3.7H.9v2.3A9 9 0 0 0 9 18z"
      />
      <path fill="#FBBC05" d="M3.9 10.8a5.4 5.4 0 0 1 0-3.5V5H.9a9 9 0 0 0 0 8.1l3-2.3z" />
      <path fill="#EA4335" d="M9 3.6c1.3 0 2.5.5 3.5 1.4l2.6-2.6A9 9 0 0 0 .9 5l3 2.3C4.6 5.2 6.6 3.6 9 3.6z" />
    </svg>
  )
}

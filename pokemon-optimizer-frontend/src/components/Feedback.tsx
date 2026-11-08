import { useId, useRef, useState, type FormEvent } from 'react'
import { useLocation } from 'react-router'
import { sendFeedback, sendRating } from '../api'

const MAX = 2000

type Status = { kind: 'idle' } | { kind: 'sending' } | { kind: 'sent' } | { kind: 'error'; message: string }

/**
 * Opens a small form for anonymous feedback. "corner" is the floating button on larger screens (on phones it
 * would cover sticky save bars), "link" sits in the footer on every screen.
 */
export function Feedback({ variant }: { variant: 'corner' | 'link' }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const textarea = useRef<HTMLTextAreaElement>(null)
  const titleId = useId()
  const { pathname } = useLocation()
  const [message, setMessage] = useState('')
  const [status, setStatus] = useState<Status>({ kind: 'idle' })

  function open() {
    if (status.kind === 'sent') {
      setMessage('')
      setStatus({ kind: 'idle' })
    }
    dialog.current?.showModal()
    // showModal focuses the first control, which is now a star: landing there would preview "1 star" and
    // let Enter send it by accident, so start in the message box instead
    textarea.current?.focus()
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (message.trim() === '') return
    setStatus({ kind: 'sending' })
    try {
      await sendFeedback(message.trim(), pathname)
      setStatus({ kind: 'sent' })
    } catch (error: unknown) {
      setStatus({ kind: 'error', message: error instanceof Error ? error.message : String(error) })
    }
  }

  return (
    <>
      {variant === 'corner' ? (
        <button
          onClick={open}
          className="btn btn-secondary btn-sm fixed right-5 bottom-5 z-30 hidden rounded-full shadow-lg sm:inline-flex"
        >
          Feedback
        </button>
      ) : (
        <button onClick={open} className="text-brand underline-offset-2 hover:underline">
          Send feedback
        </button>
      )}

      <dialog
        ref={dialog}
        aria-labelledby={titleId}
        // a click on the backdrop lands on the dialog element itself
        onClick={(e) => e.target === dialog.current && dialog.current.close()}
        className="m-auto w-[min(32rem,calc(100vw-2rem))] rounded-xl bg-surface p-0 text-left text-ink ring-1 ring-line-strong backdrop:bg-black/60"
      >
        <form onSubmit={submit} className="p-5">
          <h2 id={titleId} className="text-lg font-bold">
            Feedback
          </h2>
          {status.kind === 'sent' ? (
            <>
              <p className="mt-3 text-sm text-ink-soft">Thanks, it's been sent. It helps decide what gets fixed next.</p>
              <div className="mt-5 flex justify-end">
                <button
                  type="button"
                  onClick={() => dialog.current?.close()}
                  className="btn btn-primary"
                >
                  Close
                </button>
              </div>
            </>
          ) : (
            <>
              <StarRating />
              <h3 className="mt-6 border-t border-line pt-5 font-bold">Tell us more</h3>
              <p className="mt-1 text-sm text-subtle">
                A bug, a wrong result, something you'd like. It's anonymous: only your message and this page's address are kept.
              </p>
              <textarea
                value={message}
                onChange={(e) => setMessage(e.target.value.slice(0, MAX))}
                ref={textarea}
                rows={5}
                aria-label="Your feedback"
                className="mt-3 block w-full resize-y rounded-md bg-raised p-3 text-base text-ink ring-1 ring-line-strong placeholder:text-subtle focus:outline-none focus:ring-2 focus:ring-brand"
                placeholder="What happened, or what would help?"
              />
              <div className="mt-1 text-right text-xs tabular-nums text-subtle">
                {message.length.toLocaleString()} / {MAX.toLocaleString()}
              </div>
              {status.kind === 'error' && <p className="mt-2 text-sm text-red-400">Couldn't send it ({status.message}).</p>}
              <div className="mt-4 flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => dialog.current?.close()}
                  className="btn btn-ghost"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={message.trim() === '' || status.kind === 'sending'}
                  className="btn btn-primary"
                >
                  {status.kind === 'sending' ? 'Sending…' : 'Send'}
                </button>
              </div>
            </>
          )}
        </form>
      </dialog>
    </>
  )
}

// One rating per browser: anonymous ratings can't be edited later, so this stops double counting from the same person
const RATED_KEY = 'dexpresso.rated.v1'
const STAR_WORDS = ['', 'Poor', 'Not great', 'Okay', 'Good', 'Excellent']

function loadRated(): number | null {
  try {
    const value = Number(localStorage.getItem(RATED_KEY))
    return value >= 1 && value <= 5 ? value : null
  } catch {
    return null
  }
}

/** Its own part of the dialog: a click on a star sends the rating straight away, no message needed. */
function StarRating() {
  const { pathname } = useLocation()
  const [rated, setRated] = useState<number | null>(loadRated)
  const [hover, setHover] = useState(0)
  const [status, setStatus] = useState<Status>({ kind: 'idle' })
  const labelId = useId()

  async function rate(stars: number) {
    setStatus({ kind: 'sending' })
    try {
      await sendRating(stars, pathname)
      try {
        localStorage.setItem(RATED_KEY, String(stars))
      } catch {
        // storage blocked: the rating still counts, they just see the stars again next time
      }
      setRated(stars)
      setStatus({ kind: 'sent' })
    } catch (error: unknown) {
      setStatus({ kind: 'error', message: error instanceof Error ? error.message : String(error) })
    }
  }

  if (rated !== null) {
    return (
      <div className="rounded-lg bg-raised/60 p-4 ring-1 ring-line">
        <p className="font-bold">Rate DexPresso</p>
        <p className="mt-1 flex items-center gap-2 text-sm text-ink-soft">
          <Stars filled={rated} className="h-4 w-4" />
          {status.kind === 'sent' ? 'Thanks for rating!' : `You rated it ${rated} out of 5.`}
        </p>
      </div>
    )
  }

  const shown = hover || 0
  return (
    <div className="rounded-lg bg-raised/60 p-4 ring-1 ring-line">
      <p id={labelId} className="font-bold">
        Rate DexPresso
      </p>
      <div className="mt-2 flex items-center gap-3">
        <div role="group" aria-labelledby={labelId} className="flex" onMouseLeave={() => setHover(0)}>
          {[1, 2, 3, 4, 5].map((n) => (
            <button
              key={n}
              type="button"
              onClick={() => rate(n)}
              onMouseEnter={() => setHover(n)}
              onFocus={() => setHover(n)}
              onBlur={() => setHover(0)}
              disabled={status.kind === 'sending'}
              aria-label={`${n} star${n === 1 ? '' : 's'}: ${STAR_WORDS[n]}`}
              className="grid h-11 w-11 place-items-center rounded-lg transition hover:scale-110 disabled:opacity-60"
            >
              <Star filled={n <= shown} className="h-8 w-8" />
            </button>
          ))}
        </div>
        <span className="text-sm text-ink-soft" aria-hidden="true">
          {status.kind === 'sending' ? 'Sending…' : STAR_WORDS[shown]}
        </span>
      </div>
      {status.kind === 'error' && <p className="mt-2 text-sm text-red-400">Couldn't send it ({status.message}).</p>}
    </div>
  )
}

function Stars({ filled, className }: { filled: number; className: string }) {
  return (
    <span className="flex" aria-hidden="true">
      {[1, 2, 3, 4, 5].map((n) => (
        <Star key={n} filled={n <= filled} className={className} />
      ))}
    </span>
  )
}

function Star({ filled, className }: { filled: boolean; className: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden="true">
      <path
        d="M12 2.8l2.8 5.9 6.4.8-4.7 4.4 1.2 6.4L12 17.2l-5.7 3.1 1.2-6.4-4.7-4.4 6.4-.8z"
        strokeWidth="1.6"
        strokeLinejoin="round"
        className={filled ? 'fill-brand stroke-brand' : 'fill-none stroke-line-strong'}
      />
    </svg>
  )
}

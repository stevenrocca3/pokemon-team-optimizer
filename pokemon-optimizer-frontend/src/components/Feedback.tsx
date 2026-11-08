import { useId, useRef, useState, type FormEvent } from 'react'
import { useLocation } from 'react-router'
import { sendFeedback } from '../api'

const MAX = 2000

type Status = { kind: 'idle' } | { kind: 'sending' } | { kind: 'sent' } | { kind: 'error'; message: string }

/**
 * Opens a small form for anonymous feedback. "corner" is the floating button on larger screens (on phones it
 * would cover sticky save bars), "link" sits in the footer on every screen.
 */
export function Feedback({ variant }: { variant: 'corner' | 'link' }) {
  const dialog = useRef<HTMLDialogElement>(null)
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
          className="fixed bottom-5 right-5 z-30 hidden rounded-full bg-raised px-4 py-2 text-sm font-semibold text-ink shadow-lg ring-1 ring-line-strong transition hover:bg-raised-strong sm:block"
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
            Send feedback
          </h2>
          {status.kind === 'sent' ? (
            <>
              <p className="mt-3 text-sm text-ink-soft">Thanks, it's been sent. It helps decide what gets fixed next.</p>
              <div className="mt-5 flex justify-end">
                <button
                  type="button"
                  onClick={() => dialog.current?.close()}
                  className="rounded-md bg-brand px-4 py-2 text-sm font-bold text-on-brand transition hover:brightness-110"
                >
                  Close
                </button>
              </div>
            </>
          ) : (
            <>
              <p className="mt-1 text-sm text-subtle">
                A bug, a wrong result, something you'd like. It's anonymous: only your message and this page's address are kept.
              </p>
              <textarea
                value={message}
                onChange={(e) => setMessage(e.target.value.slice(0, MAX))}
                rows={5}
                autoFocus
                aria-label="Your feedback"
                className="mt-3 block w-full resize-y rounded-md bg-raised p-3 text-base text-ink ring-1 ring-line-strong placeholder:text-faint focus:outline-none focus:ring-2 focus:ring-brand"
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
                  className="rounded-md px-4 py-2 text-sm font-semibold text-ink-soft hover:text-ink"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={message.trim() === '' || status.kind === 'sending'}
                  className="rounded-md bg-brand px-4 py-2 text-sm font-bold text-on-brand transition hover:brightness-110 disabled:opacity-50"
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

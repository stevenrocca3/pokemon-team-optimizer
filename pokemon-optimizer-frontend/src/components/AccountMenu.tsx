import { useEffect, useId, useRef, useState } from 'react'
import { deleteAccount, getMe, LOGIN_URL, logout } from '../api'
import { useCollection } from '../collection/collection-context'

type Busy = { kind: 'idle' } | { kind: 'working' } | { kind: 'error'; action: 'sign out' | 'delete'; message: string }

/**
 * The header's account control. Guests get a Log in link; a logged-in Trainer gets a menu with Sign out and
 * Delete account. Both end with a full page load, so every page starts over from a clean, logged-out state.
 */
export function AccountMenu() {
  const collection = useCollection()
  const loggedIn = collection.status === 'ready' && collection.storage === 'account'

  if (collection.status !== 'ready') return null
  if (!loggedIn) {
    return (
      // a round icon on phones, where the search bar needs the room; the words from sm up
      <a href={LOGIN_URL} aria-label="Log in" className="btn btn-secondary btn-sm w-9 rounded-full px-0 sm:w-auto sm:rounded-lg sm:px-3">
        <svg viewBox="0 0 24 24" className="h-5 w-5 sm:hidden" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <circle cx="12" cy="8" r="4" />
          <path d="M4 21a8 8 0 0 1 16 0" strokeLinecap="round" />
        </svg>
        <span className="hidden sm:inline">Log in</span>
      </a>
    )
  }
  return <SignedIn pokemonCount={collection.list.length} />
}

function SignedIn({ pokemonCount }: { pokemonCount: number }) {
  const [name, setName] = useState<string | null>(null)
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState<Busy>({ kind: 'idle' })
  const menuId = useId()
  const titleId = useId()
  const wrapper = useRef<HTMLDivElement>(null)
  const dialog = useRef<HTMLDialogElement>(null)
  const cancel = useRef<HTMLButtonElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const menu = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let cancelled = false
    getMe()
      .then((me) => !cancelled && setName(me.name))
      .catch(() => {}) // the menu still works without a name
    return () => {
      cancelled = true
    }
  }, [])

  // close the menu on a click outside it or on Escape
  useEffect(() => {
    if (!open) return
    const onPointer = (e: PointerEvent) => {
      if (!wrapper.current?.contains(e.target as Node)) setOpen(false)
    }
    // keyboard: focus starts on the first item, arrows move between items, Escape closes and returns to the button
    menu.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false)
        trigger.current?.focus()
        return
      }
      if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return
      const items = [...(menu.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])]
      const at = items.indexOf(document.activeElement as HTMLElement)
      if (at === -1) return
      e.preventDefault()
      items[(at + (e.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length].focus()
    }
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  async function run(action: 'sign out' | 'delete') {
    setBusy({ kind: 'working' })
    try {
      await (action === 'delete' ? deleteAccount() : logout())
      window.location.assign('/')
    } catch (error: unknown) {
      setBusy({ kind: 'error', action, message: error instanceof Error ? error.message : String(error) })
    }
  }

  function askToDelete() {
    setOpen(false)
    setBusy({ kind: 'idle' })
    dialog.current?.showModal()
    // the safe choice gets focus, so Enter never deletes by accident
    cancel.current?.focus()
  }

  const initial = (name ?? '?').trim().charAt(0).toUpperCase() || '?'
  const pokemon = `${pokemonCount.toLocaleString()} Pokémon`

  return (
    <div ref={wrapper} className="relative">
      <button
        ref={trigger}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        aria-label={name ? `Account: ${name}` : 'Account'}
        onClick={() => setOpen((o) => !o)}
        className="flex h-9 w-9 items-center justify-center rounded-full bg-raised text-sm font-bold text-ink ring-1 ring-line-strong hover:bg-raised-strong"
      >
        {initial}
      </button>

      {open && (
        <div
          ref={menu}
          id={menuId}
          role="menu"
          className="absolute right-0 mt-2 w-60 rounded-xl bg-surface p-1.5 text-sm shadow-xl ring-1 ring-line-strong"
        >
          <p className="truncate px-3 pt-2 pb-1 text-ink">{name ?? 'Signed in'}</p>
          <p className="px-3 pb-2 text-xs text-muted">{pokemon} saved to your account</p>
          <div className="my-1 border-t border-line" />
          <button
            type="button"
            role="menuitem"
            disabled={busy.kind === 'working'}
            onClick={() => run('sign out')}
            className="block w-full rounded-lg px-3 py-2 text-left text-ink-soft hover:bg-raised hover:text-ink"
          >
            Sign out
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={askToDelete}
            className="block w-full rounded-lg px-3 py-2 text-left text-ink-soft hover:bg-raised hover:text-ink"
          >
            Delete account…
          </button>
          {busy.kind === 'error' && busy.action === 'sign out' && (
            <p role="alert" className="px-3 py-2 text-xs text-ink-soft">
              Couldn't sign out: {busy.message}
            </p>
          )}
        </div>
      )}

      <dialog
        ref={dialog}
        aria-labelledby={titleId}
        onClick={(e) => e.target === dialog.current && busy.kind !== 'working' && dialog.current.close()}
        className="m-auto w-[min(28rem,calc(100vw-2rem))] rounded-xl bg-surface p-0 text-left text-ink ring-1 ring-line-strong backdrop:bg-black/60"
      >
        <div className="p-5">
          <h2 id={titleId} className="text-lg font-bold">
            Delete your account?
          </h2>
          <p className="mt-3 text-sm text-ink-soft">
            This permanently deletes your account and the {pokemon} in your Collection. It can't be undone. Anonymous
            feedback and star ratings aren't tied to you, so they stay.
          </p>
          {busy.kind === 'error' && busy.action === 'delete' && (
            <p role="alert" className="mt-3 text-sm text-ink">
              Nothing was deleted: {busy.message}
            </p>
          )}
          <div className="mt-5 flex justify-end gap-2">
            <button
              ref={cancel}
              type="button"
              disabled={busy.kind === 'working'}
              onClick={() => dialog.current?.close()}
              className="btn btn-secondary"
            >
              Keep my account
            </button>
            <button
              type="button"
              disabled={busy.kind === 'working'}
              onClick={() => run('delete')}
              className="btn btn-danger"
            >
              {busy.kind === 'working' ? 'Deleting…' : 'Delete everything'}
            </button>
          </div>
        </div>
      </dialog>
    </div>
  )
}

/** How long an add can be undone (#115). */
export const UNDO_MS = 8000

/**
 * A floating "Added X to your box" with a prominent Undo, for UNDO_MS. The bar underneath drains over that time;
 * whoever shows it removes it when the time is up (the page owns the timer, so undo and expiry can't race).
 */
export function UndoToast({ message, onUndo }: { message: string; onUndo: () => void }) {
  return (
    <div
      role="status"
      aria-label="Added to your box"
      className="animate-rise fixed inset-x-4 bottom-4 z-50 mx-auto max-w-md overflow-hidden rounded-xl bg-raised-strong shadow-2xl ring-1 ring-line-strong sm:bottom-6"
    >
      <div className="flex items-center gap-3 px-4 py-3">
        <span
          aria-hidden
          className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-brand/20 text-sm font-black text-brand"
        >
          +
        </span>
        <p className="min-w-0 flex-1 text-sm text-ink">{message}</p>
        <button type="button" onClick={onUndo} className="btn btn-primary h-9 shrink-0 px-4 text-sm">
          Undo
        </button>
      </div>
      <div className="h-1 bg-line">
        <div className="undo-drain h-full origin-left bg-brand" style={{ animationDuration: `${UNDO_MS}ms` }} />
      </div>
    </div>
  )
}

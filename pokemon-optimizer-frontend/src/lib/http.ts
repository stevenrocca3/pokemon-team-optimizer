/** The request needs a login (401). Pages switch to guest mode or show a Log in link. */
export class NotLoggedInError extends Error {
  // pages that just show error.message (an import's Save) need a sentence, not ""
  constructor() {
    super("You're logged out. Log in again, then try once more.")
    this.name = 'NotLoggedInError'
  }
}

const DEFAULT_TIMEOUT_MS = 20_000

export type ApiInit = RequestInit & {
  /** how long to wait before giving up; the Team Builder passes a longer one */
  timeoutMs?: number
}

/**
 * fetch for the API: gives up on a hung request, and turns every failure into a sentence a player can act on,
 * never a bare status code. A cancel from the caller's own signal is rethrown as is, so pages can ignore it.
 */
export async function apiFetch(url: string, { timeoutMs = DEFAULT_TIMEOUT_MS, signal, ...init }: ApiInit = {}): Promise<Response> {
  const controller = new AbortController()
  let timedOut = false
  const timer = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, timeoutMs)
  const forwardCancel = () => controller.abort(signal?.reason)
  signal?.addEventListener('abort', forwardCancel)

  let response: Response
  try {
    response = await fetch(url, { ...init, signal: controller.signal })
  } catch (error: unknown) {
    if (signal?.aborted) throw error
    if (timedOut) throw new Error('DexPresso took too long to answer. Try again in a moment.', { cause: error })
    throw new Error("Can't reach DexPresso. Check your connection and try again.", { cause: error })
  } finally {
    clearTimeout(timer)
    signal?.removeEventListener('abort', forwardCancel)
  }

  if (response.ok) return response
  if (response.status === 401) throw new NotLoggedInError()
  throw new Error(await explain(response))
}

/** The server's own ProblemDetail message when it sent one, else a sentence for the status. */
async function explain(response: Response): Promise<string> {
  const retryAfter = Number(response.headers.get('Retry-After'))
  const wait = Number.isFinite(retryAfter) && retryAfter > 0 ? `${retryAfter} seconds` : 'a moment'
  if (response.status === 429) return `You're going a bit fast. Try again in ${wait}.`

  try {
    const body = await response.json()
    if (typeof body?.detail === 'string' && body.detail !== '') return body.detail
  } catch {
    // not JSON: a proxy's error page, or an empty body
  }

  if (response.status === 413) return 'That file is too big to upload.'
  if (response.status === 404) return "That couldn't be found."
  if (response.status === 403) return 'Your session expired. Reload the page and try again.'
  if (response.status >= 500) return "DexPresso isn't answering right now. Try again in a minute."
  return `That didn't work (error ${response.status}). Try again, or send feedback if it keeps happening.`
}

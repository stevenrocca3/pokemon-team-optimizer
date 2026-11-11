/** Small helpers for the analytics pages (#124). */

/** A saved battle's match id (a UUID) as the analysis job id the /match/ URLs use (no dashes). */
export function jobIdFor(matchId: string): string {
  return matchId.replaceAll('-', '')
}

/** An analysis job id as the saved battle's match id, or null when it isn't one. */
export function matchIdFor(jobId: string): string | null {
  return /^[0-9a-f]{32}$/.test(jobId) ? jobId.replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/, '$1-$2-$3-$4-$5') : null
}

/** One decimal place: 75 -> "75.0". */
export function one(value: number): string {
  return value.toFixed(1)
}

/** One decimal with its sign, a real minus: 10 -> "+10.0", -27 -> "−27.0". */
export function signed(value: number): string {
  return (value > 0 ? '+' : value < 0 ? '−' : '') + one(Math.abs(value))
}

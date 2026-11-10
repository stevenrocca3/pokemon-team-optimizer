/** The Import page's phone demo: screenshot an appraisal, pick screenshots, watch them read, save. One loop. */
export const IMPORT_DEMO_MS = 10000

export type ImportDemoFrame =
  | { phase: 'appraise'; phaseMs: number; bars: number; flash: boolean }
  | { phase: 'pick'; phaseMs: number; picked: number; tapAdd: boolean }
  | { phase: 'read'; phaseMs: number; scan: number; rows: number }
  | { phase: 'saved'; phaseMs: number; shown: number }

const APPRAISE_END = 2400
const PICK_END = 5000
const READ_END = 7600
const clamp = (v: number, lo = 0, hi = 1) => Math.min(hi, Math.max(lo, v))

export function importDemoFrame(ms: number): ImportDemoFrame {
  const t = ((ms % IMPORT_DEMO_MS) + IMPORT_DEMO_MS) % IMPORT_DEMO_MS
  if (t < APPRAISE_END) return { phase: 'appraise', phaseMs: t, bars: clamp((t - 300) / 1200), flash: t >= 1900 && t < 2200 }
  if (t < PICK_END) {
    const p = t - APPRAISE_END
    return { phase: 'pick', phaseMs: p, picked: clamp(Math.floor(p / 500), 0, 3), tapAdd: p >= 1900 && p < 2300 }
  }
  if (t < READ_END) {
    const p = t - PICK_END
    return { phase: 'read', phaseMs: p, scan: clamp(p / 1200), rows: p < 1000 ? 0 : Math.min(3, Math.floor((p - 1000) / 400) + 1) }
  }
  const p = t - READ_END
  return { phase: 'saved', phaseMs: p, shown: Math.min(3, Math.floor(p / 200)) }
}

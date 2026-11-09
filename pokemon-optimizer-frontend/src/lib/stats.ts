/** An in-battle stat to two decimals. A threshold rounds up, so reaching the shown number always reaches it. */
export function formatStat(value: number, threshold = false): string {
  // the small tolerance keeps float noise (113.20000000001) from rounding a clean value up a step
  const shown = threshold ? Math.ceil(value * 100 - 1e-7) / 100 : Math.round(value * 100) / 100
  return shown.toFixed(2)
}

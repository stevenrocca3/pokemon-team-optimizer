/** Readable name from a move ID like "POWER_UP_PUNCH" → "Power Up Punch". */
export function moveName(id: string): string {
  return id
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

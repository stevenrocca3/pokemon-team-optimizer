import type { BattleRow, PlayerAnalytics } from '../types'

/** One saved battle; tests set only what they look at. */
export function row(n: number, fields: Partial<BattleRow> = {}): BattleRow {
  return {
    matchId: `0123456789ab4def8123456789abcd${String(n).padStart(2, '0')}`.replace(
      /(.{8})(.{4})(.{4})(.{4})(.{12})/,
      '$1-$2-$3-$4-$5',
    ),
    createdAt: `2026-11-${String(10 - (n % 9)).padStart(2, '0')}T12:00:00Z`,
    userTeam: ['Swampert', 'Cradily', 'Clodsire'],
    oppTeam: ['Morpeko', 'Gastrodon', 'Doublade'],
    score: 61,
    tier: 'ace',
    outcome: 'WIN',
    turnsLost: 4,
    shieldEfficiencyPct: null,
    timingAccuracyPct: 66.7,
    ...fields,
  }
}

/** A logged-in player with 12 battles: improving, two ranked teams, rank unlocked at 74.3 (Ace). */
export function playerAnalytics(fields: Partial<PlayerAnalytics> = {}): PlayerAnalytics {
  return {
    access: 'full',
    battles: 12,
    history: [row(1, { score: 88, tier: 'veteran', outcome: null }), row(2)],
    rank: {
      locked: false,
      battles: 12,
      required: 10,
      score: 74.3,
      tier: { key: 'ace', name: 'Ace / Mid-Ladder Execution' },
    },
    trend: {
      direction: 'IMPROVING',
      velocity: 10,
      recentAverage: 80,
      earlierAverage: 70,
      rolling: { last5: 80, last10: 75, last25: null },
      focus: 'switch',
      summary:
        'Up 10.0 points: your last 5 battles average 80.0, against 70.0 in the 7 before. Biggest gain: switch mistakes, 0.0 per battle now against 2.0 before.',
    },
    topTeams: [
      {
        team: ['Swampert', 'Cradily', 'Clodsire'],
        battles: 7,
        wins: 5,
        losses: 1,
        averageScore: 78.4,
        winRatePct: 75,
        rankScore: 77,
      },
      {
        team: ['Medicham', 'Lickitung', 'Noctowl'],
        battles: 3,
        wins: 1,
        losses: 2,
        averageScore: 70,
        winRatePct: 40,
        rankScore: 58,
      },
    ],
    ...fields,
  }
}

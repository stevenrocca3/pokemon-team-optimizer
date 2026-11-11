import type { AnalysisResult, Finding, TurnLogEntry } from '../types'

/** A turn_log entry with quiet defaults; tests set only what they look at. */
export function entry(turn: number, fields: Partial<TurnLogEntry> = {}): TurnLogEntry {
  return {
    turn,
    timestamp_ms: turn * 500,
    user_active: 'Swampert',
    opp_active: 'Togekiss',
    user_hp_pct: 100,
    opp_hp_pct: 100,
    user_energy: 0,
    opp_energy: null,
    action_detected: 'NONE',
    actor: null,
    move_name: null,
    move_id: null,
    shield_used: false,
    opp_shield_used: false,
    user_shields: 2,
    opp_shields: 2,
    user_remaining: 3,
    opp_remaining: 3,
    switch_clock_s: 0,
    hud_visible: true,
    events: [],
    ...fields,
  }
}

export function finding(turn: number, message: string, fields: Partial<Finding> = {}): Finding {
  return { turn, timestamp_ms: turn * 500, category: 'timing', deduction: 12, message, confidence: 'high', ...fields }
}

/**
 * A 40-turn (20 s) battle: Swampert vs Togekiss, Togekiss faints at turn 20 and Charizard comes in, with a timing
 * error at turn 16 and a wasted shield at turn 30.
 */
export function sampleResult(): AnalysisResult {
  const turns = Array.from({ length: 40 }, (_, t) =>
    entry(t, {
      opp_active: t < 21 ? 'Togekiss' : 'Charizard',
      opp_hp_pct: t < 21 ? 100 - t * 5 : 100 - (t - 21) * 3,
      user_hp_pct: 100 - t,
      user_energy: (t % 20) * 3,
    }),
  )
  const findings = [
    finding(16, 'Turn 16: Threw Charge Move on 2-turn alignment (+1 free Mud Shot given to opponent)'),
    finding(30, 'Turn 30: wasted a shield on Body Slam (resisted, 70% HP left)', { category: 'shield', deduction: 15 }),
  ]
  return {
    turn_log: {
      match_id: 'm1',
      user_team: ['Swampert'],
      opponent_team: ['Togekiss', 'Charizard'],
      cp: {},
      turn_log: turns,
      video: { fps: 30, duration_ms: 20_000, turns_read: 40, turns_dropped: 0 },
    },
    report: {
      match_id: 'm1',
      score: 73,
      tier: { key: 'ace', name: 'Ace / Mid-Ladder Execution', min: 50 },
      counts: { timing_errors: 1, mismanaged_shields: 1, suboptimal_switches: 0, suboptimal_charges: 0 },
      deductions: { timing_errors: 12, mismanaged_shields: 15, suboptimal_switches: 0, suboptimal_charges: 0 },
      findings,
      summary: {
        fatal_flaws: [findings[1].message, findings[0].message],
        shield_audit: 'Used 1 shield, wasted 1.',
        turn_efficiency:
          'Turns lost to suboptimal timing: 2 (1 free fast move given to the opponent). Off-alignment throws: 0 of 1.',
      },
      metrics: {
        charged_moves_thrown: 1,
        off_alignment_throws: 0,
        free_turns_given: 2,
        shields_used: 1,
        switch_lag_turns: 0,
        catches: 0,
        missed_catches: 0,
      },
    },
  }
}

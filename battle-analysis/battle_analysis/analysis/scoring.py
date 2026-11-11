"""
The Legend benchmark score: every audit's errors, the spec's deduction formula, the grade tier, and the blunt summary.

    Score = 100 - 12 x timing errors - 15 x mismanaged shields - 15 x suboptimal switches - 10 x suboptimal charges

floored at 0. Suboptimal switches are switch lag plus missed catches. The same turn_log always gives the same report.
"""
from collections.abc import Callable

from battle_analysis.analysis.history import battle_outcome, history_metrics
from battle_analysis.analysis.alignment import audit_alignment
from battle_analysis.analysis.bait import audit_move_choice
from battle_analysis.analysis.tactics import audit_catches, audit_shields, audit_switch_lag

COSTS = {'timing_errors': 12, 'mismanaged_shields': 15, 'suboptimal_switches': 15, 'suboptimal_charges': 10}
CATEGORY_COST = {'timing': 12, 'shield': 15, 'switch': 15, 'charge': 10}
TIERS = [
    (90, 'legend', 'Legend Tier Execution'),
    (75, 'veteran', 'Veteran / Expert Tier Execution'),
    (50, 'ace', 'Ace / Mid-Ladder Execution'),
    (0, 'critical', 'Critical Execution Flaws (Needs Major Rebuilding)'),
]


def compute_score(timing_errors: int, mismanaged_shields: int, suboptimal_switches: int, suboptimal_charges: int) -> int:
    counts = {'timing_errors': timing_errors, 'mismanaged_shields': mismanaged_shields,
              'suboptimal_switches': suboptimal_switches, 'suboptimal_charges': suboptimal_charges}
    return max(0, 100 - sum(COSTS[k] * v for k, v in counts.items()))


def tier_for(score: int) -> dict:
    for floor, key, name in TIERS:
        if score >= floor:
            return {'key': key, 'name': name, 'min': floor}
    raise ValueError(score)


def _plural(n: int, word: str) -> str:
    return f'{n} {word}' + ('' if n == 1 else 's')


AUDIT_STAGES = {
    'alignment': 'Auditing fast move turns...',
    'shields': 'Evaluating shield decisions...',
    'switches': 'Checking switch timing...',
    'charges': 'Reviewing charged move choices...',
    'score': 'Scoring against the Legend benchmark...',
}


def analyze_match(log: dict, progress: Callable[[str], None] | None = None) -> dict:
    """The report for a turn_log. `progress(line)` hears each audit's status line just before it runs."""
    say = progress or (lambda line: None)
    say(AUDIT_STAGES['alignment'])
    alignment = audit_alignment(log)
    say(AUDIT_STAGES['shields'])
    shields = audit_shields(log)
    say(AUDIT_STAGES['switches'])
    lag = audit_switch_lag(log)
    catches = audit_catches(log)
    say(AUDIT_STAGES['charges'])
    choice = audit_move_choice(log)
    say(AUDIT_STAGES['score'])

    findings = []

    def add(category: str, item: dict, confidence: str = 'high'):
        findings.append({'turn': item['turn'], 'timestamp_ms': item['timestamp_ms'], 'category': category,
                         'deduction': CATEGORY_COST[category], 'message': item['message'], 'confidence': confidence})

    for t in alignment['throws']:
        if t['verdict'] == 'TIMING_ERROR':
            add('timing', t, t.get('confidence', 'high'))
    for s in shields['shields']:
        if s['verdict'] == 'WASTEFUL':
            add('shield', s)
    for item in lag['lags']:
        add('switch', item)
    for c in catches['catches']:
        if c['verdict'] == 'MISSED':
            add('switch', c)
    for f in choice['findings']:
        add('charge', f)
    findings.sort(key=lambda f: (f['turn'], f['category']))

    counts = {
        'timing_errors': alignment['timing_errors'],
        'mismanaged_shields': shields['mismanaged'],
        'suboptimal_switches': len(lag['lags']) + catches['missed'],
        'suboptimal_charges': choice['suboptimal_charges'],
    }
    score = compute_score(**counts)

    ranked = sorted(findings, key=lambda f: (-f['deduction'], f['turn']))
    fatal = [f['message'] for f in ranked[:3]] or ['No execution errors detected.']
    throws = alignment['throws']
    free = alignment['free_fast_moves_given']
    turn_efficiency = (f"Turns lost to suboptimal timing: {alignment['free_turns_given']} "
                       f"({_plural(free, 'free fast move')} given to the opponent). "
                       f"Off-alignment throws: {alignment['optimal_throws']} of {len(throws)}.")
    used = shields['used']
    shield_audit = f"Used {_plural(used, 'shield')}, wasted {shields['mismanaged']}." if used else 'No shields used.'
    # each wasted shield's own sentence is in `findings` (category 'shield'), for the dashboard to list under this line

    return {
        'match_id': log.get('match_id'),
        'outcome': battle_outcome(log),
        'history': history_metrics(alignment, shields, lag),
        'score': score,
        'tier': tier_for(score),
        'counts': counts,
        'deductions': {k: COSTS[k] * v for k, v in counts.items()},
        'findings': findings,
        'summary': {'fatal_flaws': fatal, 'shield_audit': shield_audit, 'turn_efficiency': turn_efficiency},
        'metrics': {
            'charged_moves_thrown': len(throws),
            'off_alignment_throws': alignment['optimal_throws'],
            'free_turns_given': alignment['free_turns_given'],
            'shields_used': used,
            'switch_lag_turns': lag['lag_turns'],
            'catches': catches['caught'],
            'missed_catches': catches['missed'],
        },
        'audits': {'alignment': alignment, 'shields': shields, 'switch_lag': lag, 'catches': catches,
                   'move_choice': choice},
    }

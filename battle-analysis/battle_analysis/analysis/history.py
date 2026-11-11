"""
What a stored battle record needs beyond the score: who won, and the history metrics (spec: Battle Performance
Analytics §1.1). Both feed the player's history: win rate per team, and trends.
"""

FAINTED = 0.5  # HP % at or under this on the last reading is a knock-out (the bar's last sliver reads as 0-0.5)


def battle_outcome(log: dict) -> str | None:
    """
    'WIN' or 'LOSS' from the last reading with the HUD up: the battle is over when one side's last Pokémon is
    down. None when it can't be told: the recording stops mid-battle, or both last Pokémon fall together (a tie).
    """
    seen = [e for e in log['turn_log'] if e.get('hud_visible') and e.get('user_hp_pct') is not None
            and e.get('opp_hp_pct') is not None]
    if not seen:
        return None
    last = seen[-1]
    user_out = last['user_hp_pct'] <= FAINTED and (last.get('user_remaining') or 0) <= 1
    opp_out = last['opp_hp_pct'] <= FAINTED and (last.get('opp_remaining') or 0) <= 1
    if user_out == opp_out:
        return None
    return 'WIN' if opp_out else 'LOSS'


def _pct(part: int, whole: int) -> float | None:
    return round(100 * part / whole, 1) if whole else None


def history_metrics(alignment: dict, shields: dict, lag: dict) -> dict:
    """
    turns_lost: free turns handed to the opponent by throwing on alignment, plus turns lost to a slow switch.
    timing_accuracy_pct: of the throws that had an alignment to play, how many were off it (None without any).
    shield_efficiency_pct: of the shields used, how many were justified (None when no shield was used).
    """
    played = alignment['optimal_throws'] + alignment['timing_errors']
    return {
        'turns_lost': alignment['free_turns_given'] + lag['lag_turns'],
        'timing_accuracy_pct': _pct(alignment['optimal_throws'], played),
        'shield_efficiency_pct': _pct(shields['used'] - shields['mismanaged'], shields['used']),
    }

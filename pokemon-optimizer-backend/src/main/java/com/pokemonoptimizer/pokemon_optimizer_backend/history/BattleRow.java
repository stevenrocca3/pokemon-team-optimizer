package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One battle in a player's history, without its (large) full result: what lists and analytics need. */
public record BattleRow(UUID matchId, Instant createdAt, List<String> userTeam, List<String> oppTeam, BigDecimal score,
                        String tier, String outcome, Integer turnsLost, BigDecimal shieldEfficiencyPct,
                        BigDecimal timingAccuracyPct, int timingErrors, int mismanagedShields, int suboptimalSwitches,
                        int suboptimalCharges)
{
}

package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pokemonoptimizer.pokemon_optimizer_backend.history.BattleRow;

/** GET /api/v1/analytics/summary for a logged-in player: their history and rank (#121), their trend (#122)
 * and their top 3 teams (#123). */
public record AnalyticsSummary(String access, int battles, List<BattleRow> history, Rank rank, Trend trend,
                               List<TeamRanking> topTeams)
{
    /** The player's rank: locked (no score, no tier, ever) until they've analysed `required` battles. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Rank(boolean locked, int battles, int required, Integer remaining, BigDecimal score, Tier tier) {}

    public record Tier(String key, String name) {}

    /** What a guest gets instead: that they're a guest, and where to log in. No player data. */
    public record GuestSummary(String access, String loginUrl) {}
}

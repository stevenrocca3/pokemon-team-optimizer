package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import java.math.BigDecimal;
import java.util.List;

/**
 * One of a player's best teams (#123): the three species (in their latest battle's order), how many battles and
 * results, the average execution score, the estimated win rate, and the composite rank score they're ordered by.
 */
public record TeamRanking(List<String> team, int battles, int wins, int losses, BigDecimal averageScore,
                          BigDecimal winRatePct, BigDecimal rankScore)
{
}

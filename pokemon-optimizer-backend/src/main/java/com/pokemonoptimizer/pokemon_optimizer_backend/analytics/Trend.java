package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import java.math.BigDecimal;

/**
 * Whether a player is improving or declining (#122). `velocity` is the last 5 battles' average minus the 15 before
 * them; `focus` is the mistake category that moved the score most between the two (timing, shield, switch, charge).
 */
public record Trend(Direction direction, BigDecimal velocity, BigDecimal recentAverage, BigDecimal earlierAverage,
                    Rolling rolling, String focus, String summary)
{
    public enum Direction { IMPROVING, STABLE, DECLINING, NOT_ENOUGH_DATA }

    /** Average score over the last 5, 10 and 25 battles; null until there are that many. */
    public record Rolling(BigDecimal last5, BigDecimal last10, BigDecimal last25) {}
}

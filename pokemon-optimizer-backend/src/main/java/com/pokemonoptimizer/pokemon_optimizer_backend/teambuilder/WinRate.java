package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

/** A sampled win rate with its Wilson 95% interval. Ties count as half a win, so {@code wins} can end in .5. */
public record WinRate(double rate, double low, double high, int battles)
{
    private static final double Z = 1.96;   // 95%

    public static WinRate of(double wins, int battles)
    {
        double p = wins / battles;
        double z2n = Z * Z / battles;
        double centre = (p + z2n / 2) / (1 + z2n);
        double halfWidth = Z * Math.sqrt(p * (1 - p) / battles + z2n / (4 * battles)) / (1 + z2n);
        return new WinRate(p, Math.max(0, centre - halfWidth), Math.min(1, centre + halfWidth), battles);
    }
}

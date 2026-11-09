package com.pokemonoptimizer.pokemon_optimizer_backend.calculator;

/**
 * Stardust and Candy to power a Pokémon up between two levels. The arrays are POKEMON_UPGRADE_SETTINGS from
 * Niantic's game master (PokeMiners mirror, checked 2026-11-09): entry {@code i} prices both power-ups at whole
 * level {@code i + 1} (L -> L+0.5 and L+0.5 -> L+1). Candy XL replaces Candy from level 40.
 */
public final class PowerUpCost
{
    /** What a power-up costs: Stardust, Candy, and Candy XL (levels 40 and up). */
    public record Cost(int stardust, int candy, int xlCandy) {}

    private static final int[] STARDUST = {
        200, 200, 400, 400, 600, 600, 800, 800, 1000, 1000, 1300, 1300, 1600, 1600, 1900, 1900, 2200, 2200, 2500, 2500,
        3000, 3000, 3500, 3500, 4000, 4000, 4500, 4500, 5000, 5000, 6000, 6000, 7000, 7000, 8000, 8000, 9000, 9000,
        10000, 10000, 11000, 11000, 12000, 12000, 13000, 13000, 14000, 14000, 15000 };
    private static final int[] CANDY = {
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 6, 6, 8, 8, 10, 10,
        12, 12, 15 };
    /** levels 40 to 49 */
    private static final int[] XL_CANDY = { 10, 10, 12, 12, 15, 15, 17, 17, 20, 20 };
    private static final int XL_FROM_LEVEL = 40;
    /**
     * shadowStardustMultiplier and shadowCandyMultiplier. The game master doesn't say how the result rounds;
     * rounding up each power-up is our assumption, so Shadow costs are estimates.
     */
    private static final double SHADOW = 1.2;

    private PowerUpCost() {}

    public static Cost between(double fromLevel, double toLevel, boolean shadow)
    {
        int stardust = 0;
        int candy = 0;
        int xlCandy = 0;
        for (double level = fromLevel; level < toLevel; level += 0.5)
        {
            int whole = (int) Math.floor(level);
            stardust += scaled(STARDUST[whole - 1], shadow);
            if (whole >= XL_FROM_LEVEL)
            {
                xlCandy += scaled(XL_CANDY[whole - XL_FROM_LEVEL], shadow);
            }
            else
            {
                candy += scaled(CANDY[whole - 1], shadow);
            }
        }
        return new Cost(stardust, candy, xlCandy);
    }

    private static int scaled(int cost, boolean shadow)
    {
        // the tolerance keeps 2,500 x 1.2 = 3000.0000000000005 from rounding up to 3,001
        return shadow ? (int) Math.ceil(cost * SHADOW - 1e-9) : cost;
    }
}

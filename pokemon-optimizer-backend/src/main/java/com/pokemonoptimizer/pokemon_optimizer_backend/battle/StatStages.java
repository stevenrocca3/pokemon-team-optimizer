package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

public final class StatStages {
    // rule 2:  Multiplier: a positive stage gives (4 + stage) / 4, so +1 is 1.25× and +4 is 2×.
    // A zero or negative stage gives 4 / (4 − stage), so −1 is 0.8× and −4 is 0.5×
    public static final int MAX_STAGE = 4;
    public static double multiplier(int stage)
    {
        if (stage > 0)
        {
            return (MAX_STAGE + stage) / (double)MAX_STAGE;
        } 
        else 
        {
            return (MAX_STAGE / (double)(MAX_STAGE - stage));
        }
    }
    public static int apply(int current, int change)
    {
        current += change;
        return Math.max(-MAX_STAGE, Math.min(MAX_STAGE, current));
    }
}

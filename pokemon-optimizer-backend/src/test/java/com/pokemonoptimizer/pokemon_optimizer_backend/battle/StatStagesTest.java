package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class StatStagesTest
{
    @Test
    public void positiveStagesAddQuarters()
    {
        assertEquals(1.25, StatStages.multiplier(1), 0.0);
        assertEquals(2.0, StatStages.multiplier(4), 0.0);
    }

    @Test
    public void negativeStagesDivide()
    {
        assertEquals(0.8, StatStages.multiplier(-1), 0.0);
        assertEquals(0.5, StatStages.multiplier(-4), 0.0);
    }

    @Test
    public void zeroIsNeutral()
    {
        assertEquals(1.0, StatStages.multiplier(0), 0.0);
    }

    @Test
    public void stagesClampAtPlusMinusFour()
    {
        assertEquals(4, StatStages.apply(3, 2));
        assertEquals(-4, StatStages.apply(-3, -2));
        assertEquals(2, StatStages.apply(1, 1));
    }
}
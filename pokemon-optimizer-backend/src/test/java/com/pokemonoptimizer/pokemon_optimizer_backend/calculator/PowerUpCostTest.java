package com.pokemonoptimizer.pokemon_optimizer_backend.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.PowerUpCost.Cost;

/** Totals from Niantic's game master (POKEMON_UPGRADE_SETTINGS, PokeMiners mirror, November 2026). */
public class PowerUpCostTest
{
    @Test
    public void level1To40()
    {
        assertEquals(new Cost(270_000, 304, 0), PowerUpCost.between(1, 40, false));
    }

    @Test
    public void level40To50IsAllCandyXl()
    {
        assertEquals(new Cost(250_000, 0, 296), PowerUpCost.between(40, 50, false));
    }

    @Test
    public void oneStepCostsItsLevelsPrice()
    {   // 20 -> 20.5 and 20.5 -> 21 are both priced at level 20
        assertEquals(new Cost(2_500, 2, 0), PowerUpCost.between(20, 20.5, false));
        assertEquals(new Cost(5_000, 4, 0), PowerUpCost.between(20, 21, false));
        assertEquals(new Cost(15_000, 0, 20), PowerUpCost.between(49.5, 50, false));
    }

    @Test
    public void shadowsCostMoreEachStep()
    {   // x1.2 Stardust and Candy (and XL), rounded up each power-up: 2,500 -> 3,000 and 2 -> 3
        assertEquals(new Cost(3_000, 3, 0), PowerUpCost.between(20, 20.5, true));
        assertEquals(new Cost(18_000, 0, 24), PowerUpCost.between(49.5, 50, true));
    }

    @Test
    public void nothingWhenAlreadyThere()
    {
        assertEquals(new Cost(0, 0, 0), PowerUpCost.between(30, 30, false));
        assertEquals(new Cost(0, 0, 0), PowerUpCost.between(31, 30, false));
    }
}

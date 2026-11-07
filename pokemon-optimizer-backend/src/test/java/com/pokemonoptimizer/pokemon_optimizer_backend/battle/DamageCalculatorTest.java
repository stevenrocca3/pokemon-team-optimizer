package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class DamageCalculatorTest 
{
    // Counter: power 8. attack == defense so the stat ratio is 1.
    @Test
    public void neutralHitWithoutStab()
    {
         assertEquals(6, DamageCalculator.damage(8, 100, 100, false, 1.0));
    }

    @Test
    public void stabMultipliesBy1Point2()
    {
        assertEquals(7, DamageCalculator.damage(8, 100, 100, true, 1.0));
    }

    @Test
    public void superEffectiveMultipliesBy1Point6()
    {
        assertEquals(9, DamageCalculator.damage(8, 100, 100, false, 1.6));
    }

    @Test
    public void doubleResistedStillDoesAtLeastTheFlatOne()
    {
        assertEquals(3, DamageCalculator.damage(8, 100, 100, false, 0.390625));
    }

    @Test
    public void higherAttackThanDefenseScalesDamage()
    {
        assertEquals(8, DamageCalculator.damage(8, 150, 100, false, 1.0));
    }

    // PvPoke uses the game's float32 bonus (1.29999995...), not 1.3.
    // 0.5 * 20 * 1.3 is exactly 13.0, so true 1.3 gives 14; the game gives 13.
    @Test
    public void usesTheGamesFloatBonusNotExactly1Point3()
    {
        assertEquals(13, DamageCalculator.damage(20, 100, 100, false, 1.0));
    }
}

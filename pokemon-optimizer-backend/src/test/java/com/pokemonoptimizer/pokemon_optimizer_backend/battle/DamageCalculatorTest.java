package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

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

    // --- wiring: real Combatants and Moves ---
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();

    // Medicham 4/14/9 L20.5 vs Registeel and Sableye at 10/10/10 L25
    private static final Combatant MEDICHAM  = new Combatant(SPECIES.findById("medicham").orElseThrow(), 4, 14, 9, 20.5, false);
    private static final Combatant REGISTEEL = new Combatant(SPECIES.findById("registeel").orElseThrow(), 10, 10, 10, 25, false);
    private static final Combatant SABLEYE   = new Combatant(SPECIES.findById("sableye").orElseThrow(), 10, 10, 10, 25, false);

    @Test
    public void stabAndSuperEffectiveComeFromTheSpeciesAndMove()
    {   // fighting move from a fighting type into steel; no STAB would be 36, neutral 27
        assertEquals(44, DamageCalculator.damage(MEDICHAM, REGISTEEL, MOVES.findById("DYNAMIC_PUNCH").orElseThrow()));
    }

    @Test
    public void offTypeMoveGetsNoStabAndCanBeResisted()
    {   // ice from a fighting/psychic into steel; wrongly given STAB would be 12, neutral 15
        assertEquals(10, DamageCalculator.damage(MEDICHAM, REGISTEEL, MOVES.findById("ICE_PUNCH").orElseThrow()));
    }

    @Test
    public void effectivenessUsesBothDefenderTypes()
    {   // fighting into dark/ghost = 1.6 x 0.390625; neutral 55, ghost only 22
        assertEquals(35, DamageCalculator.damage(MEDICHAM, SABLEYE, MOVES.findById("DYNAMIC_PUNCH").orElseThrow()));
    }

    @Test
    public void attackerStageAndDefenderStageBothCount()
    {   // attacker +2, defender -2 -> 97; swapping the two stages would give 20
        assertEquals(97, DamageCalculator.damage(MEDICHAM, 2, REGISTEEL, -2, MOVES.findById("DYNAMIC_PUNCH").orElseThrow()));
    }

    @Test
    public void loweredAttackAndRaisedDefenseCutDamage()
    {   // attacker -2, defender +2 -> 20
        assertEquals(20, DamageCalculator.damage(MEDICHAM, -2, REGISTEEL, 2, MOVES.findById("DYNAMIC_PUNCH").orElseThrow()));
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

public class BattleTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();

    private static final Combatant MEDICHAM  = new Combatant(SPECIES.findById("medicham").orElseThrow(), 4, 14, 9, 20.5, false);
    private static final Combatant REGISTEEL = new Combatant(SPECIES.findById("registeel").orElseThrow(), 10, 10, 10, 25, false);
    private static final Move COUNTER = MOVES.findById("COUNTER").orElseThrow();   // 2 turns, 4 dmg to Registeel
    private static final Move LOCK_ON = MOVES.findById("LOCK_ON").orElseThrow();   // 1 turn, 1 dmg to Medicham

    @Test
    public void fastMoveDamageLandsOnTheMovesLastTurn()
    {   // Counter lands turns 2,4,6...: 34 hits to KO 133 HP -> turn 68.
        // If damage landed on the first turn (1,3,5...) it would end on turn 67.
        BattleResult result = Battle.simulate(
            MEDICHAM, new Moveset(COUNTER, List.of()),
            REGISTEEL, new Moveset(LOCK_ON, List.of()));
        assertEquals(68, result.turns());
        assertEquals(31, result.hpA());   // 99 - 68 Lock-Ons
        assertEquals(0, result.hpB());
    }

    @Test
    public void energyIsCappedAt100()
    {   // uncapped would be 34 x 6 = 204 and 68 x 5 = 340
        BattleResult result = Battle.simulate(
            MEDICHAM, new Moveset(COUNTER, List.of()),
            REGISTEEL, new Moveset(LOCK_ON, List.of()));
        assertEquals(100, result.energyA());
        assertEquals(100, result.energyB());
    }

    @Test
    public void mirrorMatchEndsInADoubleKo()
    {   // both land 3 dmg on the same turns; both reach 0 on turn 66
        BattleResult result = Battle.simulate(
            MEDICHAM, new Moveset(COUNTER, List.of()),
            MEDICHAM, new Moveset(COUNTER, List.of()));
        assertEquals(66, result.turns());
        assertEquals(0, result.hpA());
        assertEquals(0, result.hpB());
    }

     private static final Move DYNAMIC_PUNCH = MOVES.findById("DYNAMIC_PUNCH").orElseThrow(); // 45 energy, 44 dmg to Registeel
    private static final Move ICE_PUNCH     = MOVES.findById("ICE_PUNCH").orElseThrow();     // 40 energy, 10 dmg to Registeel
    private static final Move ROCK_SMASH    = MOVES.findById("ROCK_SMASH").orElseThrow();    // 3 turns, 4 dmg to Medicham

    @Test
    public void chargedMoveIsThrownAsSoonAsItsAffordable()
    {   // Counter x8 -> 48 energy -> Dynamic Punch on turn 17, again on 33: 4x8x2 + 44x2 >= 133
        BattleResult result = Battle.simulate(
            MEDICHAM, new Moveset(COUNTER, List.of(DYNAMIC_PUNCH)),
            REGISTEEL, new Moveset(LOCK_ON, List.of()));
        assertEquals(33, result.turns());      // 32 without the dead turn after each charged move
        assertEquals(0, result.hpB());
        assertEquals(0, result.energyA());     // 90 gained - 2 x 45 spent
    }

    @Test
    public void chargedMoveKoCancelsTheVictimsFastMoveThatTurn()
    {   // Registeel's Lock-On also lands on turn 33; it must not hit
        BattleResult result = Battle.simulate(
            MEDICHAM, new Moveset(COUNTER, List.of(DYNAMIC_PUNCH)),
            REGISTEEL, new Moveset(LOCK_ON, List.of()));
        assertEquals(68, result.hpA());        // 67 if the KO'd Registeel still hits
    }

    @Test
    public void fastMoveInProgressFloatsToTheTurnAfterAChargedMove()
    {   // Rock Smash is 3 turns; ones caught mid-move by Ice Punch land early on T+1
        BattleResult result = Battle.simulate(
            MEDICHAM, new Moveset(COUNTER, List.of(ICE_PUNCH)),
            REGISTEEL, new Moveset(ROCK_SMASH, List.of()));
        assertEquals(58, result.turns());      // 55 without the dead turn
        assertEquals(23, result.hpA());        // 27 if a floating Rock Smash is lost
    }

     @Test
    public void shieldedChargedMoveDoesOneDamage()
    {   // 0-1: Registeel shields the first Dynamic Punch (1 dmg instead of 44)
        BattleResult result = Battle.simulate(
            MEDICHAM, new Moveset(COUNTER, List.of(DYNAMIC_PUNCH)),
            REGISTEEL, new Moveset(LOCK_ON, List.of()),
            new ShieldScenario(0, 1));
        assertEquals(48, result.turns());      // 33 if the shield blocks nothing
        assertEquals(53, result.hpA());
        assertEquals(0, result.shieldsB());
    }

    @Test
    public void eachShieldIsUsedOnce()
    {   // 0-2: first two Dynamic Punches shielded, then shields are gone
        BattleResult result = Battle.simulate(
            MEDICHAM, new Moveset(COUNTER, List.of(DYNAMIC_PUNCH)),
            REGISTEEL, new Moveset(LOCK_ON, List.of()),
            new ShieldScenario(0, 2));
        assertEquals(51, result.turns());
        assertEquals(0, result.shieldsB());
    }

    @Test
    public void shieldsAreOnlySpentByTheDefender()
    {   // 2-2: Registeel never throws a charged move, so Medicham keeps both shields
        BattleResult result = Battle.simulate(
            MEDICHAM, new Moveset(COUNTER, List.of(DYNAMIC_PUNCH)),
            REGISTEEL, new Moveset(LOCK_ON, List.of()),
            new ShieldScenario(2, 2));
        assertEquals(51, result.turns());
        assertEquals(2, result.shieldsA());
        assertEquals(0, result.shieldsB());
    }
}

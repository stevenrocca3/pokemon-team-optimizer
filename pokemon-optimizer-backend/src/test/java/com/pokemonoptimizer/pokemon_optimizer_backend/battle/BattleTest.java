package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        BattleResult result = Battle.simulate(MEDICHAM, COUNTER, REGISTEEL, LOCK_ON);
        assertEquals(68, result.turns());
        assertEquals(31, result.hpA());   // 99 - 68 Lock-Ons
        assertEquals(0, result.hpB());
    }

    @Test
    public void energyIsCappedAt100()
    {   // uncapped would be 34 x 6 = 204 and 68 x 5 = 340
        BattleResult result = Battle.simulate(MEDICHAM, COUNTER, REGISTEEL, LOCK_ON);
        assertEquals(100, result.energyA());
        assertEquals(100, result.energyB());
    }

    @Test
    public void mirrorMatchEndsInADoubleKo()
    {   // both land 3 dmg on the same turns; both reach 0 on turn 66
        BattleResult result = Battle.simulate(MEDICHAM, COUNTER, MEDICHAM, COUNTER);
        assertEquals(66, result.turns());
        assertEquals(0, result.hpA());
        assertEquals(0, result.hpB());
    }
}

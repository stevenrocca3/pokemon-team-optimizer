package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class LearnsetTest
{
    private static final Learnset MEDICHAM = new SpeciesLookup().findById("medicham").orElseThrow().learnset();

    @Test
    public void allowsMovesFromTheSpeciesPool()
    {   // Counter is a Medicham fast move; Dynamic Punch and Ice Punch are charged moves
        assertTrue(MEDICHAM.allows("COUNTER", List.of("DYNAMIC_PUNCH", "ICE_PUNCH")));
    }

    @Test
    public void rejectsAFastMoveTheSpeciesCantLearn()
    {   // Mud Shot is Swampert's
        assertFalse(MEDICHAM.allows("MUD_SHOT", List.of("DYNAMIC_PUNCH")));
    }

    @Test
    public void rejectsAChargedMoveTheSpeciesCantLearn()
    {
        assertFalse(MEDICHAM.allows("COUNTER", List.of("DYNAMIC_PUNCH", "HYDRO_CANNON")));
    }

    @Test
    public void rejectsAChargedMoveInTheFastSlot()
    {   // Dynamic Punch is Medicham's, but it's a charged move
        assertFalse(MEDICHAM.allows("DYNAMIC_PUNCH", List.of("ICE_PUNCH")));
    }
}
package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;

import static org.junit.jupiter.api.Assertions.assertEquals;


import org.junit.jupiter.api.Test;

public class MoveLookupTest 
{
    private static final MoveLookup ml = new MoveLookup();
    @Test
    public void testMoveCount() 
    {   //testing to make sure all moves that should be loaded are
        assertEquals(352, ml.findAll().size());

    }

    @Test
    public void testMoveLookup() 
    {
       
        assertEquals("Power-Up Punch", ml.findById("POWER_UP_PUNCH").orElseThrow().name()); //making sure moves gets stored in proper index
        assertEquals("POWER_UP_PUNCH", ml.findById("POWER_UP_PUNCH").orElseThrow().moveId());
    }

    @Test
    public void fastMoveHasBattleStats()
    {   // Counter: fast move, gains energy, takes 2 turns
        Move counter = ml.findById("COUNTER").orElseThrow();
        assertEquals("fighting", counter.type());
        assertEquals(8, counter.power());
        assertEquals(0, counter.energy());
        assertEquals(6, counter.energyGain());
        assertEquals(2, counter.turns());
    }

    @Test
    public void chargedMoveHasBattleStats()
    {   // Power-Up Punch: charged move, costs 35 energy
        Move pup = ml.findById("POWER_UP_PUNCH").orElseThrow();
        assertEquals(20, pup.power());
        assertEquals(35, pup.energy());
        assertEquals(0, pup.energyGain());
    }

}
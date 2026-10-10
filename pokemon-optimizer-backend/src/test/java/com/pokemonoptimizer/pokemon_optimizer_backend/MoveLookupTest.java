package com.pokemonoptimizer.pokemon_optimizer_backend;

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

}
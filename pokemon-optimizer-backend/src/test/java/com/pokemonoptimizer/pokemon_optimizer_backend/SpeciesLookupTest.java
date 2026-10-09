package com.pokemonoptimizer.pokemon_optimizer_backend;

import static org.junit.jupiter.api.Assertions.assertEquals; 

import org.junit.jupiter.api.Test;

public class SpeciesLookupTest 
{
    private static final SpeciesLookup sl = new SpeciesLookup();
    @Test
    public void testCount() 
    {   //testing to make sure all pokemon that should be loaded are
        assertEquals(1132, sl.findAll().size());

    }

    @Test
    public void testPlainSpecies() 
    {
       
        assertEquals("Bulbasaur", sl.findById("bulbasaur").orElseThrow().speciesName()); //making sure species gets stored in proper index
        assertEquals("bulbasaur", sl.findById("bulbasaur").orElseThrow().speciesId());
        assertEquals(128, sl.findById("bulbasaur").orElseThrow().baseSta());
        assertEquals(118, sl.findById("bulbasaur").orElseThrow().baseAtk());
        assertEquals(111, sl.findById("bulbasaur").orElseThrow().baseDef());
    }

    @Test 
    public void testFormAndBaseSpecies()
    {
        String key = "typhlosion"; 
        String key2 = "typhlosion_hisuian";
        SpeciesType typhlosion = sl.findById(key).orElseThrow(); //checking lookups ignore case
        SpeciesType typhlosion_hisuian = sl.findById(key2).orElseThrow();
     
        assertEquals(177, typhlosion_hisuian.baseSta()); // making sure bsts are different from hisuian vs reg form
        assertEquals(238, typhlosion_hisuian.baseAtk());
        assertEquals(172, typhlosion_hisuian.baseDef());
        assertEquals(186, typhlosion.baseSta());
        assertEquals(223, typhlosion.baseAtk());
        assertEquals(173, typhlosion.baseDef());

    }

}

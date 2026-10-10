package com.pokemonoptimizer.pokemon_optimizer_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;  


import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

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
        SpeciesType typhlosion = sl.findById(key).orElseThrow(); 
        SpeciesType typhlosion_hisuian = sl.findById(key2).orElseThrow();
     
        assertEquals(177, typhlosion_hisuian.baseSta()); // making sure bsts are different from hisuian vs reg form
        assertEquals(238, typhlosion_hisuian.baseAtk());
        assertEquals(172, typhlosion_hisuian.baseDef());
        assertEquals(186, typhlosion.baseSta());
        assertEquals(223, typhlosion.baseAtk());
        assertEquals(173, typhlosion.baseDef());

    }

    @Test 
    public void testFastMoves()
    {
        String key = "medicham"; 
        SpeciesType medicham = sl.findById(key).orElseThrow();
        List<String> expected = new ArrayList<>();
        expected.add("COUNTER");
        expected.add("PSYCHO_CUT");

        assertEquals(expected, medicham.learnset().fastMoves());
    }

    @Test 
    public void testChargedMovesAndHasEmpySet()
    {
        String key = "medicham"; 
        SpeciesType medicham = sl.findById(key).orElseThrow();
        List<String> expected = new ArrayList<>();
        // DYNAMIC_PUNCH, ICE_PUNCH, PSYCHIC, POWER_UP_PUNCH
        expected.add("DYNAMIC_PUNCH");
        expected.add("ICE_PUNCH");
        expected.add("PSYCHIC");
        expected.add("POWER_UP_PUNCH");

        assertTrue(medicham.learnset().eliteMoves().isEmpty());
        assertEquals(expected, medicham.learnset().chargedMoves());
    }

    @Test 
    public void testEliteMoves()
    {
        String key = "muk"; 
        SpeciesType muk = sl.findById(key).orElseThrow();
        HashSet<String> exp = new HashSet<>();
        //  ACID and LICK
        exp.add("LICK");
        exp.add("ACID");
        Set<String> expected = Set.copyOf(exp);
        assertEquals(expected, muk.learnset().eliteMoves());
    }

}

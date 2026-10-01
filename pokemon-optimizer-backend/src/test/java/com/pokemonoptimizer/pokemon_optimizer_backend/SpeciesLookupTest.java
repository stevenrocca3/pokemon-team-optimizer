package com.pokemonoptimizer.pokemon_optimizer_backend;

import static org.junit.jupiter.api.Assertions.assertEquals; 

import org.junit.jupiter.api.Test;

public class SpeciesLookupTest 
{
    private static final SpeciesLookup sl = new SpeciesLookup();
    @Test
    public void testRowCount() 
    {   //testing to make sure all pokemon that should be loaded are
        assertEquals(1166, sl.findAll().size());

    }

    @Test
    public void testPlainSpecies() 
    {
        // 1,Bulbasaur,,128,118,111
        sl.getId(1, "");
        assertEquals("Bulbasaur", sl.findById("1").orElseThrow().name()); //making sure species gets stored in proper index
        assertEquals("", sl.findById("1").orElseThrow().form());
        assertEquals(128, sl.findById("1").orElseThrow().baseSta());
        assertEquals(118, sl.findById("1").orElseThrow().baseAtk());
        assertEquals(111, sl.findById("1").orElseThrow().baseDef());
    }

    @Test 
    public void testFormAndCapitalizationSpecies()
    {
        String key = sl.getId(706, "HISUiAn"); 
        String key2 = sl.getId(706, "");
        SpeciesType goodraHis = sl.findById(key).orElseThrow(); //checking lookups ignore case
        SpeciesType goodra = sl.findById(key2).orElseThrow();
        //706,Goodra,,207,220,242 706,Goodra,Hisuian,190,211,255
        assertEquals("706-hisuian", key); // checking key works properly as string + form pair
        assertEquals(190, goodraHis.baseSta()); // making sure bsts are different from hisuian vs reg form
        assertEquals(211, goodraHis.baseAtk());
        assertEquals(255, goodraHis.baseDef());
        assertEquals("Hisuian", goodraHis.form()); 
        assertEquals("706", key2); // checking hisuian form doesn't overwrite reg
        assertEquals(207, goodra.baseSta());
        assertEquals(220, goodra.baseAtk());
        assertEquals(242, goodra.baseDef());

    }

}

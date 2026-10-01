package com.pokemonoptimizer.pokemon_optimizer_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;   

import java.util.HashMap;

import org.junit.jupiter.api.Test;

public class SpeciesLookupTest 
{
    @Test
    public void testRowCount() 
    {
        SpeciesLookup sl = new SpeciesLookup();
        HashMap<String, SpeciesType> data = sl.loadSpecies(); //testing to make sure all pokemon that should be loaded are
        assertEquals(1166, data.size());

    }

    @Test
    public void testPlainSpecies() 
    {
        SpeciesLookup sl = new SpeciesLookup();
        HashMap<String, SpeciesType> data = sl.loadSpecies();
        String buildKey = sl.getId(1, "");
        SpeciesType species = data.get(buildKey);
        assertNotNull(data.get(buildKey), "Must not be null");
        SpeciesType st = new SpeciesType(1, "bulbasaur", "", 128, 118, 111);
        assertEquals("Bulbasaur", species.name()); //making sure species gets stored in proper index
        assertEquals(st.dexNumber(), species.dexNumber());
        assertEquals(st.form(), species.form());
        assertEquals(st.baseSta(), species.baseSta());
        assertEquals(st.baseAtk(), species.baseAtk());
        assertEquals(st.baseDef(), species.baseDef());
        
    }

    @Test 
    public void testFormAndCapitalizationSpecies()
    {
        SpeciesLookup sl = new SpeciesLookup();
        HashMap<String, SpeciesType> data = sl.loadSpecies();
        String buildKey = sl.getId(706, "HISUIAN"); // making sure it converts to lower case after getKey
        String buildKeyReg = sl.getId(706, "");
        SpeciesType goodraHis = data.get(buildKey);
        SpeciesType goodra = data.get(buildKeyReg);
        assertNotNull(data.get(buildKey), "Must not be null");
        assertNotNull(data.get(buildKeyReg), "Must not be null");
        String key = sl.getId(goodraHis.dexNumber(), goodraHis.form());
        String key2 = sl.getId(goodra.dexNumber(), goodra.form());

        //706,Goodra,,207,220,242 706,Goodra,Hisuian,190,211,255
        assertEquals("706-hisuian", key); // checking key works properly as string + form pair
        assertEquals(190, goodraHis.baseSta()); // making sure bsts are different from hisuian vs reg form
        assertEquals(211, goodraHis.baseAtk());
        assertEquals(255, goodraHis.baseDef());
        assertEquals("Hisuian", goodraHis.form()); //checking lookups ignore case
        assertEquals("706", key2); // checking hisuian form doesn't overwrite reg
        assertEquals(207, goodra.baseSta());
        assertEquals(220, goodra.baseAtk());
        assertEquals(242, goodra.baseDef());

    }

}

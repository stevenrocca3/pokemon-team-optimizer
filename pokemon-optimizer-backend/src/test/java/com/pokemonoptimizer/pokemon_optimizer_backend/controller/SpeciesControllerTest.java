package com.pokemonoptimizer.pokemon_optimizer_backend.controller;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;  

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.SpeciesDetail;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.SpeciesOption;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

public class SpeciesControllerTest {
    private static final SpeciesLookup sl = new SpeciesLookup();
    private static final SpeciesController sc = new SpeciesController(sl);

    @Test
    public void testCheckUniqueIds()
    {
        HashSet<String> hs = new HashSet<>();
        List<SpeciesOption> options = sc.speciesOption();
        for (SpeciesOption option: options)
        {
            hs.add(option.id());
        }
        assertEquals(options.size(), hs.size());
    }

    @Test 
    public void formsGetTheirOwnId()
    {
        List<SpeciesOption> options = sc.speciesOption();
        SpeciesOption target = null;
        for (SpeciesOption option: options)
        {
            if (option.id().equals("typhlosion_hisuian"))
            {
                target = option;
            }
        }
        assertNotNull(target, "Hisuian Typhlosion missing from options");
        assertEquals("Typhlosion (Hisuian)", target.name());
        assertEquals("typhlosion_hisuian", target.id());
    }

    @Test
    public void baseFormsGetOwnId()
    {
        List<SpeciesOption> options = sc.speciesOption();
        SpeciesOption target = null;
        for (SpeciesOption option: options)
        {
            if (option.id().equals("typhlosion"))
            {
                target = option;
            }
        }
        assertNotNull(target, "Typhlosion missing from options");
        assertEquals("Typhlosion", target.name());
        assertEquals("typhlosion", target.id());
    }

    @Test
    public void everyIdWorks()
    {
        List<SpeciesOption> options = sc.speciesOption();
        for (SpeciesOption option: options)
        {
            Optional<SpeciesType> opt = (sl.findById(option.id()));
            assertTrue(opt.isPresent(), "findById couldn't find " + option.id());
        }
    }

    @Test
    public void speciesByIdReturnsStats()
    {
        SpeciesDetail typhlosion = sc.speciesById("typhlosion");
        
        assertEquals(186, typhlosion.baseSta());
        assertEquals(223, typhlosion.baseAtk());
        assertEquals(173, typhlosion.baseDef());
    }

    @Test 
    public void speciesByIdUnknownIs404()
    {
        assertThrows(ResponseStatusException.class, () -> sc.speciesById("9999"));
    }
    
    @Test 
    public void speciesPresentShadowNot()
    {
        Optional<SpeciesType> opt = (sl.findById("bulbasaur_shadow"));
        assertTrue(opt.isEmpty(), "findById found: " + opt);
    }  
}

package com.pokemonoptimizer.pokemon_optimizer_backend;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals; 
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Optional;

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
            if (option.id().equals("706-hisuian"))
            {
                target = option;
            }
        }
        assertNotNull(target, "Hisuian Goodra missing from options");
        assertEquals("Goodra", target.name());
        assertEquals("Hisuian", target.form());
        assertEquals("706-hisuian", target.id());
    }

    @Test
    public void baseFormsGetOwnId()
    {
        List<SpeciesOption> options = sc.speciesOption();
        SpeciesOption target = null;
        for (SpeciesOption option: options)
        {
            if (option.id().equals("706"))
            {
                target = option;
            }
        }
        assertNotNull(target, "Goodra missing from options");
        assertEquals("Goodra", target.name());
        assertEquals("", target.form());
        assertEquals("706", target.id());
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
    
}

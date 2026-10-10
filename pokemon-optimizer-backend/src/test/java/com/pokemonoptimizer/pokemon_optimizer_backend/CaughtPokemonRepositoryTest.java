package com.pokemonoptimizer.pokemon_optimizer_backend;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class CaughtPokemonRepositoryTest 
{
    @Autowired 
    private CaughtPokemonRepository caughtPokemonRepository;
    @Autowired private TestEntityManager entityManager;
    @Test
    public void testIdentitySetupWorks()
    {
        //"dex": 308,"speciesName": "Medicham","speciesId": "medicham","baseStats": {"atk": 121,"def": 152,"hp": 155
        //public CaughtPokemon(String speciesId, int atkIv, int defIv, int staIv, double level)
        CaughtPokemon caughtPokemon = new CaughtPokemon("medicham", 0, 15, 15, 22.5 );
        assertNull(caughtPokemon.getId());
        CaughtPokemon saved = caughtPokemonRepository.save(caughtPokemon);
        assertNotNull(saved.getId());
        assertNotNull(caughtPokemon.getId());
    }

    @Test
    public void testSaveTwoSpecies()
    {
        Long count = caughtPokemonRepository.count();
        CaughtPokemon caughtPokemon = new CaughtPokemon("medicham", 0, 15, 15, 22.5 );
        CaughtPokemon caughtPokemon2 = new CaughtPokemon("medicham", 0, 15, 15, 22.5 );
        CaughtPokemon saved = caughtPokemonRepository.save(caughtPokemon);
        CaughtPokemon saved2 = caughtPokemonRepository.save(caughtPokemon2);
        Long count2 = caughtPokemonRepository.count();
        assertEquals(count+2, count2);
    }

    @Test
    public void testFieldsComeBackUnchanged()
    {
        CaughtPokemon caughtPokemon = new CaughtPokemon("medicham", 1, 14, 15, 22.5 );
        CaughtPokemon saved = caughtPokemonRepository.save(caughtPokemon);
        entityManager.flush();
        entityManager.clear();
        CaughtPokemon cp = caughtPokemonRepository.findById(saved.getId()).orElseThrow();
        assertEquals("medicham", cp.getSpeciesId());
        assertEquals(1, cp.getAtkIv());
        assertEquals(14, cp.getDefIv());
        assertEquals(15, cp.getStaIv());
        assertEquals(22.5, cp.getLevel());
    }
}

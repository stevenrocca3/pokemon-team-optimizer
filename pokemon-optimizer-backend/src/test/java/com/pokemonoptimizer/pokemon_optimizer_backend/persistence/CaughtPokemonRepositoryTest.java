package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
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
        Trainer ash = entityManager.persist(new Trainer("google-ash", "Ash"));
        CaughtPokemon caughtPokemon = new CaughtPokemon("medicham", 0, 15, 15, 22.5, ash);
        assertNull(caughtPokemon.getId());
        CaughtPokemon saved = caughtPokemonRepository.save(caughtPokemon);
        assertNotNull(saved.getId());
        assertNotNull(caughtPokemon.getId());
    }

    @Test
    public void testSaveTwoSpecies()
    {
        Long count = caughtPokemonRepository.count();
        Trainer ash = entityManager.persist(new Trainer("google-ash", "Ash"));
        CaughtPokemon caughtPokemon = new CaughtPokemon("medicham", 0, 15, 15, 22.5, ash);
        CaughtPokemon caughtPokemon2 = new CaughtPokemon("medicham", 0, 15, 15, 22.5, ash);
        CaughtPokemon saved = caughtPokemonRepository.save(caughtPokemon);
        CaughtPokemon saved2 = caughtPokemonRepository.save(caughtPokemon2);
        Long count2 = caughtPokemonRepository.count();
        assertEquals(count+2, count2);
    }

    @Test
    public void testFieldsComeBackUnchanged()
    {
        Trainer ash = entityManager.persist(new Trainer("google-ash", "Ash"));
        CaughtPokemon caughtPokemon = new CaughtPokemon("medicham", 1, 14, 15, 22.5, ash);
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

    @Test
    public void findByTrainerOnlyReturnsThatTrainersPokemon()
    {
        Trainer ash = entityManager.persist(new Trainer("google-ash", "Ash"));
        Trainer misty = entityManager.persist(new Trainer("google-misty", "Misty"));
        caughtPokemonRepository.save(new CaughtPokemon("medicham", 0, 15, 15, 22.5, ash));
        caughtPokemonRepository.save(new CaughtPokemon("azumarill", 0, 15, 15, 40, misty));
         List<CaughtPokemon> ashes = caughtPokemonRepository.findByTrainer(ash);

        assertEquals(1, ashes.size());
        assertEquals("medicham", ashes.get(0).getSpeciesId());
    }
}

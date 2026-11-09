package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class CaughtPokemonPersistenceTest
{
    @Autowired private TestEntityManager entityManager;
    @Autowired private CaughtPokemonRepository caughtPokemonRepository;

    @Test
    public void aPokemonWithoutALevelIsStored()
    {
        Trainer ash = entityManager.persist(new Trainer("google-ash"));
        entityManager.persist(new CaughtPokemon("medicham", 0, 15, 15, null, false, "COUNTER", List.of("ICE_PUNCH"), ash));
        entityManager.flush();
        entityManager.clear();

        assertThat(caughtPokemonRepository.findByTrainer(ash)).singleElement()
            .satisfies(p -> assertThat(p.getLevel()).isNull());
    }
}

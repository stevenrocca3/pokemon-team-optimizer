package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class TeamBuildPersistenceTest
{
    @Autowired private TestEntityManager entityManager;
    @Autowired private TeamBuildRepository teamBuilds;

    @Test
    public void aRunIsStoredWithItsLeagueCupAndSize()
    {
        long before = teamBuilds.count();
        Long id = teamBuilds.save(new TeamBuild(League.GREAT, "spellcraft", 12, Instant.parse("2026-11-09T12:00:00Z"))).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(teamBuilds.count()).isEqualTo(before + 1);
        assertThat(teamBuilds.findById(id)).get().satisfies(b -> {
            assertThat(b.getLeague()).isEqualTo(League.GREAT);
            assertThat(b.getCup()).isEqualTo("spellcraft");
            assertThat(b.getPokemonCount()).isEqualTo(12);
        });
    }
}

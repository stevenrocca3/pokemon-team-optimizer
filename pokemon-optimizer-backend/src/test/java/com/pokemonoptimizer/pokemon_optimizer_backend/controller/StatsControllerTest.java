package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.checklist.ChecklistRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.StarCount;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemonRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.RatingRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TeamBuildRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

@WebMvcTest(StatsController.class)
@Import(SecurityConfig.class)
public class StatsControllerTest
{
    @Autowired private MockMvcTester mvc;

    @MockitoBean private TeamBuildRepository teamBuilds;
    @MockitoBean private CaughtPokemonRepository caughtPokemon;
    @MockitoBean private ChecklistRepository checklist;
    @MockitoBean private TrainerRepository trainers;
    @MockitoBean private RatingRepository ratings;

    @Test
    public void anyoneCanSeeTheTotals()
    {   // no login: the home page shows them to guests
        when(teamBuilds.count()).thenReturn(1234L);
        when(caughtPokemon.count()).thenReturn(12000L);
        when(checklist.count()).thenReturn(800L);
        when(trainers.count()).thenReturn(90L);
        when(ratings.countByStars()).thenReturn(List.of(new StarCount(5, 3L), new StarCount(4, 1L)));

        assertThat(mvc.get().uri("/api/stats"))
            .hasStatusOk()
            .bodyJson().isLenientlyEqualTo("""
                {"teamsBuilt":1234,"pokemonSaved":12000,"speciesTicked":800,"trainers":90,"ratings":{"total":4,"average":4.75}}""");
    }
}

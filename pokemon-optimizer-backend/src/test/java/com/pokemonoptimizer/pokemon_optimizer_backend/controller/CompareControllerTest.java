package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.ComparedPokemon;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.IvComparison;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.IvComparisonResult;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

@WebMvcTest(CompareController.class)
@Import(SecurityConfig.class)
public class CompareControllerTest
{
    @Autowired private MockMvcTester mvc;
    @MockitoBean private IvComparison comparison;   // the battling is IvComparisonTest's job

    private static final String MEDICHAM = """
            {"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,"level":20,"fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]}""";

    private static String body(int copies)
    {
        return "{\"league\":\"GREAT\",\"pokemon\":[" + String.join(",", java.util.Collections.nCopies(copies, MEDICHAM)) + "]}";
    }

    @Test
    public void guestsCanCompareWithoutLoggingIn()
    {   // no login and no CSRF token, like the Team Builder
        when(comparison.compare(eq(League.GREAT), any())).thenReturn(new IvComparisonResult(League.GREAT, 44, "2026-10-04",
            List.of(new ComparedPokemon(1, 50, 1439, 70, 132, 512.3, null)), List.of()));

        assertThat(mvc.post().uri("/api/compare").contentType(MediaType.APPLICATION_JSON).content(body(2)))
            .hasStatusOk()
            .bodyJson().extractingPath("$.pokemon[0].wins").asNumber().isEqualTo(70);
    }

    @Test
    public void oneIsNothingToCompare()
    {
        assertThat(mvc.post().uri("/api/compare").contentType(MediaType.APPLICATION_JSON).content(body(1)))
            .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void moreThanSixIs400()
    {
        assertThat(mvc.post().uri("/api/compare").contentType(MediaType.APPLICATION_JSON).content(body(7)))
            .hasStatus(HttpStatus.BAD_REQUEST);
    }
}

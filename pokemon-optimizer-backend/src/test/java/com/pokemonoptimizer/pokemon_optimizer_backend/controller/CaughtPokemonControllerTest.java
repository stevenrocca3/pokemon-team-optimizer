package com.pokemonoptimizer.pokemon_optimizer_backend.controller;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemonRepository;

@WebMvcTest(CaughtPokemonController.class)
@MockitoBean(types = SpeciesLookup.class)
@MockitoBean(types = CaughtPokemonRepository.class)
public class CaughtPokemonControllerTest {

    @Autowired
    private MockMvcTester mvc;
    //expect "invalidAtkIv" 400 '{"speciesId":"medicham","atkIv":16,"defIv":14,"staIv":15,"level":22.5}'
    @Test
    public void outOfBoundsAtkIvReturns400()
    {
        String body = """
                 {"speciesId":"medicham","atkIv":20,"defIv":14,"staIv":15,"level":22.5}
                """;
        assertThat(mvc.post().uri("/api/collection")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors[*].field").asArray().contains("atkIv");
    }
}

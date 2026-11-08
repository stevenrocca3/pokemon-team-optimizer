package com.pokemonoptimizer.pokemon_optimizer_backend.controller;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemonRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

@WebMvcTest(CaughtPokemonController.class)
@MockitoBean(types = SpeciesLookup.class)
@MockitoBean(types = CaughtPokemonRepository.class)
@MockitoBean(types = TrainerRepository.class)
@Import(SecurityConfig.class)
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
            .with(csrf())
            .with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors[*].field").asArray().contains("atkIv");
    }

    @Test
    public void missingAtkIvReturns400()
    {
        String body = """
                 {"speciesId":"medicham","defIv":14,"staIv":15,"level":22.5}
                """;
        assertThat(mvc.post().uri("/api/collection")
            .with(csrf())
            .with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors[*].field").asArray().contains("atkIv");
    }

    @Test
    public void testNotLoggedInThrows403()
    {
        String body = """
                 {"speciesId":"medicham","atkIv":15,"defIv":14,"staIv":15,"level":22.5}
                """;
        assertThat(mvc.post().uri("/api/collection")
            .with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Autowired private SpeciesLookup speciesLookup;            // the @MockitoBean mocks, injected so we can stub them
    @Autowired private TrainerRepository trainerRepository;
    @Autowired private CaughtPokemonRepository caughtPokemonRepository;

    @BeforeEach
    void stubMocks()
    {
        when(speciesLookup.findById("medicham")).thenReturn(new SpeciesLookup().findById("medicham"));   // the real Medicham
        when(trainerRepository.findByGoogleId(any())).thenReturn(Optional.of(new Trainer("google-id", "Steven")));
        when(caughtPokemonRepository.save(any())).thenAnswer(call -> call.getArgument(0));            // save returns what it was given
    }

    @Test
    public void illegalMovesetReturns400()
    {   // Mud Shot is Swampert's; Medicham can't learn it
        String body = """
                 {"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,"level":40,
                  "fastMove":"MUD_SHOT","chargedMoves":["DYNAMIC_PUNCH"]}
                """;
        assertThat(mvc.post().uri("/api/collection")
            .with(csrf())
            .with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.detail").asString().contains("MUD_SHOT");
    }

    @Test
    public void legalMovesetIsSavedWithThePokemon()
    {
        String body = """
                 {"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,"level":40,
                  "fastMove":"COUNTER","chargedMoves":["DYNAMIC_PUNCH","ICE_PUNCH"]}
                """;
        assertThat(mvc.post().uri("/api/collection")
            .with(csrf())
            .with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .hasPathSatisfying("$.fastMove", v -> assertThat(v).asString().isEqualTo("COUNTER"))
            .hasPathSatisfying("$.chargedMoves", v -> assertThat(v).asArray().containsExactly("DYNAMIC_PUNCH", "ICE_PUNCH"));
    }
}

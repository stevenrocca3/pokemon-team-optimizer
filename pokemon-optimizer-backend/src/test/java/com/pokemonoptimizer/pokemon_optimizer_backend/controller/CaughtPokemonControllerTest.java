package com.pokemonoptimizer.pokemon_optimizer_backend.controller;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import java.util.Collections;
import java.util.List;
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
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemon;
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
        when(trainerRepository.findByGoogleId(any())).thenReturn(Optional.of(new Trainer("google-id")));
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

    @Test
    public void shadowFlagIsSavedWithThePokemon()
    {
        when(speciesLookup.findById("swampert")).thenReturn(new SpeciesLookup().findById("swampert"));
        String body = """
                 {"speciesId":"swampert","atkIv":0,"defIv":15,"staIv":15,"level":20,"shadow":true,
                  "fastMove":"MUD_SHOT","chargedMoves":["HYDRO_CANNON","EARTHQUAKE"]}
                """;
        assertThat(mvc.post().uri("/api/collection")
            .with(csrf())
            .with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .hasPathSatisfying("$.shadow", v -> assertThat(v).asBoolean().isTrue());
    }

    @Test
    public void shadowOnASpeciesThatCantBeShadowReturns400()
    {   // Medicham has never been a Shadow
        String body = """
                 {"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,"level":40,"shadow":true,
                  "fastMove":"COUNTER","chargedMoves":["DYNAMIC_PUNCH"]}
                """;
        assertThat(mvc.post().uri("/api/collection")
            .with(csrf())
            .with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.detail").asString().contains("shadow");
    }


    @Test
    public void batchSavesEveryPokemon()
    {
        when(caughtPokemonRepository.saveAll(any())).thenAnswer(call -> call.getArgument(0));
        String body = """
                {"pokemon":[
                  {"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,"level":40,"fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]},
                  {"speciesId":"medicham","atkIv":1,"defIv":15,"staIv":14,"level":40.5,"fastMove":"PSYCHO_CUT","chargedMoves":["DYNAMIC_PUNCH","ICE_PUNCH"]}
                ]}
                """;
        assertThat(mvc.post().uri("/api/collection/batch")
            .with(csrf())
            .with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .hasStatus(HttpStatus.CREATED)
            .bodyJson().extractingPath("$[*].fastMove").asArray().containsExactly("COUNTER", "PSYCHO_CUT");
    }

    @Test
    public void batchWithOneIllegalMovesetSavesNothing()
    {   // all or nothing: the player fixes #2 and retries, without duplicating #1
        String body = """
                {"pokemon":[
                  {"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,"level":40,"fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]},
                  {"speciesId":"medicham","atkIv":1,"defIv":15,"staIv":14,"level":40,"fastMove":"MUD_SHOT","chargedMoves":["ICE_PUNCH"]}
                ]}
                """;
        assertThat(mvc.post().uri("/api/collection/batch")
            .with(csrf())
            .with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.detail").asString().contains("#2").contains("MUD_SHOT");
        verify(caughtPokemonRepository, never()).saveAll(any());
    }

    @Test
    public void batchNeedsALogin()
    {
        assertThat(mvc.post().uri("/api/collection/batch")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"pokemon\":[]}"))
            .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private static final Trainer STEVEN = new Trainer("google-id");

    private void alreadyCaught(CaughtPokemon... pokemon)
    {
        when(caughtPokemonRepository.findByTrainer(any())).thenReturn(List.of(pokemon));
        when(caughtPokemonRepository.saveAll(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static CaughtPokemon medicham(int atkIv, double level, boolean shadow)
    {
        return new CaughtPokemon("medicham", atkIv, 15, 15, level, shadow, "COUNTER", List.of("ICE_PUNCH"), STEVEN);
    }

    @Test
    public void oneChargedMoveIsListedWithoutAnEmptySecond()
    {   // a null second move came back as [ICE_PUNCH, null], which the batch and team builder reject
        alreadyCaught(medicham(0, 40, false));
        assertThat(mvc.get().uri("/api/collection").with(oidcLogin()))
            .hasStatusOk()
            .bodyJson().extractingPath("$[0].chargedMoves").asArray().containsExactly("ICE_PUNCH");
    }

    @Test
    public void batchSkipsPokemonAlreadyInTheCollection()
    {   // the same file imported twice; only the new 1/15/15 is saved
        alreadyCaught(medicham(0, 40, false));
        String body = """
                {"pokemon":[
                  {"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,"level":40,"fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]},
                  {"speciesId":"medicham","atkIv":1,"defIv":15,"staIv":15,"level":40,"fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]}
                ]}
                """;
        assertThat(mvc.post().uri("/api/collection/batch").with(csrf()).with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .hasStatus(HttpStatus.CREATED)
            .bodyJson().extractingPath("$[*].atkIv").asArray().containsExactly(1);
    }

    @Test
    public void batchSavesARepeatedPokemonOnce()
    {
        alreadyCaught();
        String body = """
                {"pokemon":[
                  {"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,"level":40,"fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]},
                  {"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,"level":40,"fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]}
                ]}
                """;
        assertThat(mvc.post().uri("/api/collection/batch").with(csrf()).with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .hasStatus(HttpStatus.CREATED)
            .bodyJson().extractingPath("$.length()").asNumber().isEqualTo(1);
    }

    @Test
    public void shadowAndRegularWithTheSameIvsAreDifferentPokemon()
    {
        alreadyCaught(medicham(0, 40, true));
        String body = """
                {"pokemon":[
                  {"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,"level":40,"fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]}
                ]}
                """;
        assertThat(mvc.post().uri("/api/collection/batch").with(csrf()).with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .hasStatus(HttpStatus.CREATED)
            .bodyJson().extractingPath("$[*].shadow").asArray().containsExactly(false);
    }

    @Test
    public void batchRaisesTheLevelOfAPoweredUpDuplicate()
    {   // powered up since the last import: keep one Pokémon, at its new level
        CaughtPokemon existing = medicham(0, 40, false);
        alreadyCaught(existing);
        String body = """
                {"pokemon":[
                  {"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,"level":41,"fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]}
                ]}
                """;
        assertThat(mvc.post().uri("/api/collection/batch").with(csrf()).with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .hasStatus(HttpStatus.CREATED)
            .bodyJson().extractingPath("$[*].level").asArray().containsExactly(41.0);
        verify(caughtPokemonRepository).saveAll(argThat(saved -> saved.equals(List.of(existing))));   // updated, not a second copy
    }

    /** The Team Builder takes at most 10,000 Pokémon, so a Collection may not grow past what it can build from. */
    @Test
    public void aBatchThatWouldPushTheCollectionPast10000IsRefusedWholly()
    {
        alreadyCaught(Collections.nCopies(9_999, medicham(0, 40, false)).toArray(CaughtPokemon[]::new));
        String twoNew = """
                {"pokemon":[
                  {"speciesId":"medicham","atkIv":1,"defIv":15,"staIv":15,"level":40,"fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]},
                  {"speciesId":"medicham","atkIv":2,"defIv":15,"staIv":15,"level":40,"fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]}
                ]}
                """;
        assertThat(mvc.post().uri("/api/collection/batch").with(csrf()).with(oidcLogin())
                .contentType(MediaType.APPLICATION_JSON).content(twoNew))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.detail").asString().contains("10,000").contains("1 more");
        verify(caughtPokemonRepository, never()).saveAll(any());
    }

    @Test
    public void aBatchThatExactlyFillsTheCollectionIsSaved()
    {
        alreadyCaught(Collections.nCopies(9_999, medicham(0, 40, false)).toArray(CaughtPokemon[]::new));
        String oneNewOneAlreadyThere = """
                {"pokemon":[
                  {"speciesId":"medicham","atkIv":1,"defIv":15,"staIv":15,"level":40,"fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]},
                  {"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,"level":40,"fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]}
                ]}
                """;
        assertThat(mvc.post().uri("/api/collection/batch").with(csrf()).with(oidcLogin())
                .contentType(MediaType.APPLICATION_JSON).content(oneNewOneAlreadyThere))
            .hasStatus(HttpStatus.CREATED);
    }

    @Test
    public void addingOneToAFullCollectionIsRefused()
    {
        when(caughtPokemonRepository.countByTrainer(any())).thenReturn(10_000L);
        String body = """
                 {"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,"level":40,
                  "fastMove":"COUNTER","chargedMoves":["DYNAMIC_PUNCH","ICE_PUNCH"]}
                """;
        assertThat(mvc.post().uri("/api/collection").with(csrf()).with(oidcLogin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.detail").asString().contains("10,000");
        verify(caughtPokemonRepository, never()).save(any());
    }
}

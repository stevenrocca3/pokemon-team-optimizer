package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

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

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemon;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemonRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.MetaPresets;

@WebMvcTest(MetaPresetController.class)
@MockitoBean(types = { MetaPresets.class, CaughtPokemonRepository.class, TrainerRepository.class })
@Import(SecurityConfig.class)
public class MetaPresetControllerTest
{
    @Autowired private MockMvcTester mvc;
    @Autowired private MetaPresets presets;
    @Autowired private CaughtPokemonRepository collection;
    @Autowired private TrainerRepository trainers;

    private static final Trainer TRAINER = new Trainer("google-id");
    // PvPoke's Great League Medicham
    private static final CaughtPokemonRequest MEDICHAM = new CaughtPokemonRequest("medicham", 15, 15, 15, 49.0, "COUNTER",
        List.of("ICE_PUNCH", "PSYCHIC"), false);

    @BeforeEach
    void stub()
    {
        when(trainers.findByGoogleId(any())).thenReturn(Optional.of(TRAINER));
        when(collection.save(any())).thenAnswer(call -> call.getArgument(0));
        when(presets.preset(eq(League.GREAT), any(), eq("medicham"), eq(false))).thenReturn(Optional.of(MEDICHAM));
    }

    private static String body(String speciesId)
    {
        return """
            {"speciesId":"%s","shadow":false,"league":"GREAT","cup":null}
            """.formatted(speciesId);
    }

    // A suggested meta Pokémon is saved to the Trainer's Collection as PvPoke builds it: 201 with the saved Pokémon
    @Test
    public void savesTheMetaBuild()
    {
        assertThat(mvc.post().uri("/api/collection/meta-preset").with(csrf()).with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON).content(body("medicham")))
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .hasPathSatisfying("$.speciesId", v -> assertThat(v).asString().isEqualTo("medicham"))
            .hasPathSatisfying("$.atkIv", v -> assertThat(v).asNumber().isEqualTo(15))
            .hasPathSatisfying("$.level", v -> assertThat(v).asNumber().isEqualTo(49.0))
            .hasPathSatisfying("$.chargedMoves", v -> assertThat(v).asArray().containsExactly("ICE_PUNCH", "PSYCHIC"));
        verify(collection).save(any());
    }

    // Already in the Collection (same species, IVs and Shadow): 200 with that one, and nothing new is saved
    @Test
    public void anOwnedOneIsntAddedTwice()
    {
        CaughtPokemon owned = new CaughtPokemon("medicham", 15, 15, 15, 49.0, false, "COUNTER", List.of("ICE_PUNCH", "PSYCHIC"), TRAINER);
        when(collection.findByTrainer(TRAINER)).thenReturn(List.of(owned));

        assertThat(mvc.post().uri("/api/collection/meta-preset").with(csrf()).with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON).content(body("medicham")))
            .hasStatus(HttpStatus.OK)
            .bodyJson().hasPathSatisfying("$.speciesId", v -> assertThat(v).asString().isEqualTo("medicham"));
        verify(collection, never()).save(any());
    }

    // Not in that meta: 404, nothing saved
    @Test
    public void notInTheMetaIs404()
    {
        assertThat(mvc.post().uri("/api/collection/meta-preset").with(csrf()).with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON).content(body("magikarp")))
            .hasStatus(HttpStatus.NOT_FOUND);
        verify(collection, never()).save(any());
    }

    // A full Collection (10,000) takes no more: 400, nothing saved
    @Test
    public void aFullCollectionIs400()
    {
        when(collection.countByTrainer(TRAINER)).thenReturn(10_000L);

        assertThat(mvc.post().uri("/api/collection/meta-preset").with(csrf()).with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON).content(body("medicham")))
            .hasStatus(HttpStatus.BAD_REQUEST);
        verify(collection, never()).save(any());
    }

    // A League is required: 400 without one
    @Test
    public void aLeagueIsRequired()
    {
        assertThat(mvc.post().uri("/api/collection/meta-preset").with(csrf()).with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"speciesId":"medicham","shadow":false}
                """))
            .hasStatus(HttpStatus.BAD_REQUEST);
        verify(collection, never()).save(any());
    }

    // Guests keep their Pokémon in the browser: logged out is 401, and without the CSRF token 403
    @Test
    public void loggedInOnly()
    {
        assertThat(mvc.post().uri("/api/collection/meta-preset").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("medicham")))
            .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/api/collection/meta-preset").with(oidcLogin())
            .contentType(MediaType.APPLICATION_JSON).content(body("medicham")))
            .hasStatus(HttpStatus.FORBIDDEN);
        verify(collection, never()).save(any());
    }
}

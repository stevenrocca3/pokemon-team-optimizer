package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.pokemonoptimizer.pokemon_optimizer_backend.cups.Cup;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.CupCatalog;
import com.pokemonoptimizer.pokemon_optimizer_backend.evolution.EvolvableCandidates;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.CupRules;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TeamBuildRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilder;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.Evolution;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.EvolvedPokemon;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilderResult;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilderGate;

@WebMvcTest(TeamBuilderController.class)
@Import({SecurityConfig.class, TeamBuilderGate.class})
public class TeamBuilderControllerTest
{
    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TeamBuilder teamBuilder;

    @MockitoBean
    private EvolvableCandidates evolvable;

    @MockitoBean
    private CupCatalog cups;   // the search itself is TeamBuilderTest's job

    @MockitoBean
    private TeamBuildRepository teamBuilds;

    private static final String BODY = """
            {"league":"ULTRA","pokemon":[
              {"speciesId":"swampert","atkIv":0,"defIv":15,"staIv":14,"level":20,"fastMove":"MUD_SHOT","chargedMoves":["HYDRO_CANNON"]}
            ]}
            """;

    @Test
    public void guestsCanBuildTeamsWithoutLoggingIn()
    {   // no oidcLogin() and no csrf(): a fresh visitor may not have the XSRF-TOKEN cookie yet, like /api/rank
        when(teamBuilder.build(eq(League.ULTRA), any(), any(), any())).thenReturn(new TeamBuilderResult(League.ULTRA, 45, "2026-10-04", 1, List.of(), List.of(), null));

        assertThat(mvc.post().uri("/api/team-builder").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .hasStatusOk()
            .bodyJson().extractingPath("$.metaSize").asNumber().isEqualTo(45);
    }

    @Test
    public void aRequestWithoutALeagueIs400()
    {
        assertThat(mvc.post().uri("/api/team-builder").contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"league\":\"ULTRA\",", "")))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors[*].field").asArray().contains("league");
    }

    @Test
    public void tickedSpeciesAloneAreEnough()
    {   // a checklist-only Trainer sends no exact Pokémon
        when(teamBuilder.build(League.GREAT, List.of(), List.of(new OwnedSpecies("medicham", false), new OwnedSpecies("registeel", true)), List.of()))
            .thenReturn(new TeamBuilderResult(League.GREAT, 44, "2026-10-04", 2, List.of(), List.of(), null));

        assertThat(mvc.post().uri("/api/team-builder").contentType(MediaType.APPLICATION_JSON).content("""
                {"league":"GREAT","species":[{"speciesId":"medicham"},{"speciesId":"registeel","shadow":true}]}
                """))
            .hasStatusOk()
            .bodyJson().extractingPath("$.eligible").asNumber().isEqualTo(2);
    }

    @Test
    public void tooManyTickedSpeciesIs400()
    {
        String many = "{\"speciesId\":\"medicham\"},".repeat(2001);
        assertThat(mvc.post().uri("/api/team-builder").contentType(MediaType.APPLICATION_JSON)
                .content("{\"league\":\"GREAT\",\"species\":[" + many.substring(0, many.length() - 1) + "]}"))
            .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private static final Cup FANTASY = new Cup("fantasy-1500", "Fantasy Cup", League.GREAT, 1500, 3, List.of(),
        new CupRules(List.of(), List.of()), List.of(), "2026-11-03");

    @Test
    public void aCupBuildsForThatCup()
    {
        when(cups.find("fantasy-1500")).thenReturn(Optional.of(FANTASY));
        when(teamBuilder.build(eq(FANTASY), any(), any(), any()))
            .thenReturn(new TeamBuilderResult(League.GREAT, 30, "2026-11-03", 2, List.of(), List.of(), "fantasy-1500"));

        assertThat(mvc.post().uri("/api/team-builder").contentType(MediaType.APPLICATION_JSON).content("""
                {"league":"GREAT","cup":"fantasy-1500","species":[{"speciesId":"azumarill"}]}
                """))
            .hasStatusOk()
            .bodyJson().extractingPath("$.cup").asString().isEqualTo("fantasy-1500");
    }

    @Test
    public void aCupThatsNoLongerRunningIs400()
    {
        when(cups.find("halloween-1500")).thenReturn(Optional.empty());

        assertThat(mvc.post().uri("/api/team-builder").contentType(MediaType.APPLICATION_JSON).content("""
                {"league":"GREAT","cup":"halloween-1500","species":[{"speciesId":"azumarill"}]}
                """))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.detail").asString().contains("isn't running");
    }

    @Test
    public void aCupFromAnotherLeagueIs400()
    {
        when(cups.find("fantasy-1500")).thenReturn(Optional.of(FANTASY));

        assertThat(mvc.post().uri("/api/team-builder").contentType(MediaType.APPLICATION_JSON).content("""
                {"league":"ULTRA","cup":"fantasy-1500","species":[{"speciesId":"azumarill"}]}
                """))
            .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void aFinishedBuildIsCounted()
    {
        when(teamBuilder.build(eq(League.ULTRA), any(), any(), any())).thenReturn(new TeamBuilderResult(League.ULTRA, 45, "2026-10-04", 1, List.of(), List.of(), null));

        assertThat(mvc.post().uri("/api/team-builder").with(fromIp("10.0.0.1")).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .hasStatusOk();
        verify(teamBuilds).save(argThat(b -> b.getLeague() == League.ULTRA && b.getCup() == null
            && b.getPokemonCount() == 1 && b.getCreatedAt() != null));
    }

    @Test
    public void aRejectedBuildIsNotCounted()
    {   // a Cup that's gone is a 400, not a build
        when(cups.find("gone")).thenReturn(Optional.empty());

        assertThat(mvc.post().uri("/api/team-builder").with(fromIp("10.0.0.2")).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"league\":\"ULTRA\",", "\"league\":\"ULTRA\",\"cup\":\"gone\",")))
            .hasStatus(HttpStatus.BAD_REQUEST);
        verify(teamBuilds, never()).save(any());
    }

    /** Its own client, so the Team Builder's burst limit (8 per IP) isn't used up by the other tests in this class. */
    private static RequestPostProcessor fromIp(String ip)
    {
        return request -> { request.setRemoteAddr(ip); return request; };
    }

    // #138: "Include Evolvable Pokémon" sends the request's Pokémon through EvolvableCandidates into the search
    @Test
    public void includeEvolvableAddsTheEvolvedForms()
    {
        EvolvedPokemon swampert = new EvolvedPokemon(0, new CaughtPokemonRequest("swampert", 0, 15, 15, 20.0, "MUD_SHOT",
            List.of("HYDRO_CANNON"), false), new Evolution("mudkip", List.of()));
        when(evolvable.expand(any())).thenReturn(List.of(swampert));
        when(teamBuilder.build(eq(League.GREAT), any(), any(), eq(List.of(swampert))))
            .thenReturn(new TeamBuilderResult(League.GREAT, 44, "2026-10-04", 2, List.of(), List.of(), null));

        assertThat(mvc.post().uri("/api/team-builder").with(fromIp("10.0.0.3")).contentType(MediaType.APPLICATION_JSON).content("""
                {"league":"GREAT","includeEvolvable":true,"pokemon":[
                  {"speciesId":"mudkip","atkIv":0,"defIv":15,"staIv":15,"level":20,"fastMove":"TACKLE","chargedMoves":["DIG"]}
                ]}
                """))
            .hasStatusOk()
            .bodyJson().extractingPath("$.eligible").asNumber().isEqualTo(2);
    }

    @Test
    public void evolvedFormsAreOnlyTriedWhenAskedFor()
    {
        when(teamBuilder.build(eq(League.ULTRA), any(), any(), any())).thenReturn(new TeamBuilderResult(League.ULTRA, 45, "2026-10-04", 1, List.of(), List.of(), null));

        assertThat(mvc.post().uri("/api/team-builder").with(fromIp("10.0.0.4")).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .hasStatusOk();
        verify(evolvable, never()).expand(any());
        verify(teamBuilder).build(eq(League.ULTRA), any(), any(), eq(List.of()));
    }
}


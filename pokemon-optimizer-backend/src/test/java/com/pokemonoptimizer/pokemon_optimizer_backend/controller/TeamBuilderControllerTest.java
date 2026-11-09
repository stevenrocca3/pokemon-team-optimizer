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

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilder;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilderResult;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilderGate;

@WebMvcTest(TeamBuilderController.class)
@Import({SecurityConfig.class, TeamBuilderGate.class})
public class TeamBuilderControllerTest
{
    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TeamBuilder teamBuilder;   // the search itself is TeamBuilderTest's job

    private static final String BODY = """
            {"league":"ULTRA","pokemon":[
              {"speciesId":"swampert","atkIv":0,"defIv":15,"staIv":14,"level":20,"fastMove":"MUD_SHOT","chargedMoves":["HYDRO_CANNON"]}
            ]}
            """;

    @Test
    public void guestsCanBuildTeamsWithoutLoggingIn()
    {   // no oidcLogin() and no csrf(): a fresh visitor may not have the XSRF-TOKEN cookie yet, like /api/rank
        when(teamBuilder.build(eq(League.ULTRA), any(), any())).thenReturn(new TeamBuilderResult(League.ULTRA, 45, "2026-10-04", 1, List.of(), List.of()));

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
        when(teamBuilder.build(League.GREAT, List.of(), List.of(new OwnedSpecies("medicham", false), new OwnedSpecies("registeel", true))))
            .thenReturn(new TeamBuilderResult(League.GREAT, 44, "2026-10-04", 2, List.of(), List.of()));

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
}

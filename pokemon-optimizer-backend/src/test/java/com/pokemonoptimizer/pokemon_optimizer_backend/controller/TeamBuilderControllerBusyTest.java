package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.cups.CupCatalog;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilder;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilderGate;

/** No slots and no wait: every search finds the Team Builder full. */
@WebMvcTest(TeamBuilderController.class)
@Import({SecurityConfig.class, TeamBuilderGate.class})
@TestPropertySource(properties = {"app.team-builder.max-concurrent=0", "app.team-builder.queue-wait=0s"})
public class TeamBuilderControllerBusyTest
{
    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CupCatalog cups;

    @MockitoBean
    private TeamBuilder teamBuilder;

    @Test
    public void aFullTeamBuilderAnswers503WithRetryAfterAndNeverStartsTheSearch()
    {
        assertThat(mvc.post().uri("/api/team-builder").contentType(MediaType.APPLICATION_JSON).content("""
                {"league":"ULTRA","pokemon":[
                  {"speciesId":"swampert","atkIv":0,"defIv":15,"staIv":14,"level":20,"fastMove":"MUD_SHOT","chargedMoves":["HYDRO_CANNON"]}
                ]}
                """))
            .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
            .hasHeader("Retry-After", "15")
            .bodyJson().extractingPath("$.detail").asString().contains("busy");

        verify(teamBuilder, never()).build(any(), any());
    }
}

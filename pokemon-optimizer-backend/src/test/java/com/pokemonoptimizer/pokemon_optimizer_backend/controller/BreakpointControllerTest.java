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
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.BreakpointReport;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.Breakpoints;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

@WebMvcTest(BreakpointController.class)
@Import(SecurityConfig.class)
public class BreakpointControllerTest
{
    @Autowired private MockMvcTester mvc;
    @MockitoBean private Breakpoints breakpoints;   // the numbers are BreakpointsTest's job

    private static final String BODY = """
            {"league":"GREAT","pokemon":{"speciesId":"medicham","atkIv":0,"defIv":15,"staIv":15,
             "fastMove":"COUNTER","chargedMoves":["ICE_PUNCH"]}}
            """;

    @Test
    public void guestsCanSeeBreakpoints()
    {
        when(breakpoints.report(eq(League.GREAT), any()))
            .thenReturn(new BreakpointReport(League.GREAT, 50, 1439, "COUNTER", 110, 150, 125, 160, List.of()));

        assertThat(mvc.post().uri("/api/breakpoints").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .hasStatusOk()
            .bodyJson().extractingPath("$.cp").asNumber().isEqualTo(1439);
    }

    @Test
    public void oneThatCantPlayIs400WithTheReason()
    {
        when(breakpoints.report(eq(League.GREAT), any())).thenThrow(new IllegalArgumentException("Over 1500 CP"));

        assertThat(mvc.post().uri("/api/breakpoints").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.detail").asString().contains("Over 1500 CP");
    }
}

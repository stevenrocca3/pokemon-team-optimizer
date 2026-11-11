package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

/** Issue #120: the browser reports each finished analysis, and claims its guest battles after logging in. */
@WebMvcTest(BattleController.class)
@Import(SecurityConfig.class)
public class BattleControllerTest
{
    private static final String JOB = "0123456789abcdef0123456789abcdef";
    private static final UUID TOKEN = UUID.fromString("7f0c2c1e-4b8a-4d5e-9a61-2f3b4c5d6e7f");

    @Autowired private MockMvcTester mvc;
    @MockitoBean private BattleHistoryService service;

    private MockMvcTester.MockMvcRequestBuilder report(String jobId)
    {
        return mvc.post().uri("/api/v1/battles").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"jobId\": \"" + jobId + "\"}");
    }

    private MockMvcTester.MockMvcRequestBuilder asAsh(MockMvcTester.MockMvcRequestBuilder request)
    {
        return request.with(oidcLogin().idToken(t -> t.subject("google-ash")));
    }

    // Logged in: the battle is saved to the Trainer (their Google ID); 201 when it's newly saved.
    @Test
    public void savesToTheLoggedInTrainer()
    {
        when(service.record(eq(JOB), any())).thenReturn(true);
        assertThat(asAsh(report(JOB))).hasStatus(HttpStatus.CREATED);
        verify(service).record(JOB, new BattleOwner("google-ash", null));
    }

    // Logged in with this browser's guest token too: the token goes along, so a battle saved before logging in moves over.
    @Test
    public void passesTheGuestTokenAlongWhenLoggedIn()
    {
        when(service.record(eq(JOB), any())).thenReturn(true);
        assertThat(asAsh(report(JOB)).header("X-Guest-Token", TOKEN.toString())).hasStatus(HttpStatus.CREATED);
        verify(service).record(JOB, new BattleOwner("google-ash", TOKEN));
    }

    // Logged out: saved under the guest token the browser sends.
    @Test
    public void savesAGuestsBattleUnderTheirToken()
    {
        when(service.record(eq(JOB), any())).thenReturn(true);
        assertThat(report(JOB).header("X-Guest-Token", TOKEN.toString())).hasStatus(HttpStatus.CREATED);
        verify(service).record(JOB, BattleOwner.guest(TOKEN));
    }

    // Already in this player's history (reported twice): 200, nothing new.
    @Test
    public void reportingTwiceIsOk()
    {
        when(service.record(eq(JOB), any())).thenReturn(false);
        assertThat(asAsh(report(JOB))).hasStatus(HttpStatus.OK);
    }

    // Logged out with no guest token there's no one to save it for; a malformed token or job id is refused. Nothing saved.
    @Test
    public void refusesARequestWithNoOwnerOrBadIds()
    {
        assertThat(report(JOB)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(report(JOB).header("X-Guest-Token", "not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(asAsh(report("../etc/passwd"))).hasStatus(HttpStatus.BAD_REQUEST);
        verify(service, never()).record(anyString(), any());
    }

    // The service's refusals become statuses the browser can act on: not finished yet 409 (report again later),
    // unknown or expired 404, already someone else's 409.
    @Test
    public void mapsRefusalsToStatuses()
    {
        when(service.record(eq(JOB), any())).thenThrow(new AnalysisNotFinishedException());
        assertThat(asAsh(report(JOB))).hasStatus(HttpStatus.CONFLICT);
        when(service.record(eq(JOB), any())).thenThrow(new AnalysisNotFoundException());
        assertThat(asAsh(report(JOB))).hasStatus(HttpStatus.NOT_FOUND);
        when(service.record(eq(JOB), any())).thenThrow(new BattleOwnedElsewhereException());
        assertThat(asAsh(report(JOB))).hasStatus(HttpStatus.CONFLICT).bodyText().contains("someone else");
    }

    // Saving changes a player's history, so a request without the CSRF token is refused.
    @Test
    public void needsTheCsrfToken()
    {
        assertThat(mvc.post().uri("/api/v1/battles").with(oidcLogin()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"jobId\": \"" + JOB + "\"}")).hasStatus(HttpStatus.FORBIDDEN);
        verify(service, never()).record(anyString(), any());
    }

    // Claiming needs a login (401 without) and the guest token; it answers how many battles moved.
    @Test
    public void claimsTheGuestsBattlesForTheLoggedInTrainer()
    {
        assertThat(mvc.post().uri("/api/v1/battles/claim").with(csrf()).header("X-Guest-Token", TOKEN.toString()))
            .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(asAsh(mvc.post().uri("/api/v1/battles/claim").with(csrf()))).hasStatus(HttpStatus.BAD_REQUEST);

        when(service.claim("google-ash", TOKEN)).thenReturn(2);
        assertThat(asAsh(mvc.post().uri("/api/v1/battles/claim").with(csrf()).header("X-Guest-Token", TOKEN.toString())))
            .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.claimed").isEqualTo(2);
    }
}

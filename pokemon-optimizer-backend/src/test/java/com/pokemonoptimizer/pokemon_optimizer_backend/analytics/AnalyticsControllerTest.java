package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

/** Issue #121: analytics endpoints give guests a guest flag and nothing else; logged-in players get their own data. */
@WebMvcTest(AnalyticsController.class)
@Import({SecurityConfig.class, AnalyticsWebConfig.class})
public class AnalyticsControllerTest
{
    private static final UUID MATCH = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

    @Autowired private MockMvcTester mvc;
    @MockitoBean private AnalyticsService analytics;
    @MockitoBean private TrainerRepository trainers;

    private final Trainer ash = new Trainer("google-ash");

    @BeforeEach
    void setUp()
    {
        when(trainers.findByGoogleId("google-ash")).thenReturn(Optional.of(ash));
        when(analytics.summary(ash)).thenReturn(new AnalyticsSummary("full", 3, List.of(),
            new AnalyticsSummary.Rank(true, 3, 10, 7, null, null), null));
    }

    private MockMvcTester.MockMvcRequestBuilder asAsh(MockMvcTester.MockMvcRequestBuilder request)
    {
        return request.with(oidcLogin().idToken(t -> t.subject("google-ash")));
    }

    // A guest asking for the summary gets 200 with only the guest flag and where to log in: no count, history or
    // rank, and the analytics service (which reads player data) is never asked.
    @Test
    public void aGuestGetsOnlyTheGuestFlag()
    {
        assertThat(mvc.get().uri("/api/v1/analytics/summary")).hasStatus(HttpStatus.OK).bodyJson()
            .isStrictlyEqualTo("{\"access\": \"guest\", \"loginUrl\": \"/oauth2/authorization/google\"}");
        verifyNoInteractions(analytics);
    }

    // A logged-in player gets their full summary, for their own Trainer.
    @Test
    public void aPlayerGetsTheirFullSummary()
    {
        assertThat(asAsh(mvc.get().uri("/api/v1/analytics/summary"))).hasStatus(HttpStatus.OK).bodyJson()
            .isLenientlyEqualTo("{\"access\": \"full\", \"battles\": 3, \"history\": [], "
                + "\"rank\": {\"locked\": true, \"battles\": 3, \"required\": 10, \"remaining\": 7}}");
        verify(analytics).summary(ash);
    }

    // A locked rank leaves the score and tier out of the JSON entirely (not even as null).
    @Test
    public void aLockedRankHasNoScoreOrTierInTheJson()
    {
        assertThat(asAsh(mvc.get().uri("/api/v1/analytics/summary"))).bodyText()
            .doesNotContain("\"score\"").doesNotContain("\"tier\"");
    }

    // A session whose Trainer is gone (the account was deleted in another tab) is treated as a guest.
    @Test
    public void aSessionWithoutATrainerIsAGuest()
    {
        assertThat(mvc.get().uri("/api/v1/analytics/summary").with(oidcLogin().idToken(t -> t.subject("google-gone"))))
            .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.access").isEqualTo("guest");
        verifyNoInteractions(analytics);
    }

    // A stored battle's result goes to its owner as the stored JSON; anyone else (or a battle that isn't theirs) 404s.
    @Test
    public void aStoredBattleGoesToItsOwnerOnly()
    {
        when(analytics.battleResult(ash, MATCH)).thenReturn(Optional.of("{\"report\": {\"score\": 61}}"));
        assertThat(asAsh(mvc.get().uri("/api/v1/analytics/battles/" + MATCH))).hasStatus(HttpStatus.OK).bodyJson()
            .extractingPath("$.report.score").isEqualTo(61);

        when(analytics.battleResult(any(), any())).thenReturn(Optional.empty());
        assertThat(asAsh(mvc.get().uri("/api/v1/analytics/battles/" + MATCH))).hasStatus(HttpStatus.NOT_FOUND);
    }

    // A guest asking for a stored battle gets 404 without player data being looked up.
    @Test
    public void aGuestCantReadAStoredBattle()
    {
        assertThat(mvc.get().uri("/api/v1/analytics/battles/" + MATCH)).hasStatus(HttpStatus.NOT_FOUND);
        verify(analytics, never()).battleResult(any(), any());
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The player's battle analytics (#121). Open to everyone: a guest gets a guest flag (and where to log in) and never
 * any player data; a logged-in player gets their own.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController
{
    static final String LOGIN_URL = "/oauth2/authorization/google";

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics)
    {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public Object summary(AnalyticsViewer viewer)
    {
        return switch (viewer)
        {
            case AnalyticsViewer.Guest guest -> new AnalyticsSummary.GuestSummary("guest", LOGIN_URL);
            case AnalyticsViewer.Player player -> analytics.summary(player.trainer());
        };
    }

    /** A stored battle's full result (turn log + report), for its owner; 404 for anyone else. */
    @GetMapping("/battles/{matchId}")
    public ResponseEntity<String> battle(AnalyticsViewer viewer, @PathVariable UUID matchId)
    {
        if (!(viewer instanceof AnalyticsViewer.Player player))
        {
            return ResponseEntity.notFound().build();
        }
        return analytics.battleResult(player.trainer(), matchId)
            .map(json -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

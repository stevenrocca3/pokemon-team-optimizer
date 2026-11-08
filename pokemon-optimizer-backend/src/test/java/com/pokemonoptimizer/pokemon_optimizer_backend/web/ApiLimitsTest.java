package com.pokemonoptimizer.pokemon_optimizer_backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.web.RateLimiter.Limit;

public class ApiLimitsTest
{
    private static String limitName(String method, String path)
    {
        return ApiLimits.forRequest(method, path).map(Limit::name).orElse("none");
    }

    @Test
    public void theTeamBuilderHasItsOwnTightLimit()
    {   // each search keeps every CPU core busy for seconds
        assertThat(limitName("POST", "/api/team-builder")).isEqualTo("team-builder");
        assertThat(ApiLimits.forRequest("POST", "/api/team-builder").orElseThrow().capacity()).isLessThanOrEqualTo(10);
    }

    @Test
    public void importsHaveTheirOwnLimit()
    {
        assertThat(limitName("POST", "/api/import/preview")).isEqualTo("import");
    }

    @Test
    public void feedbackAndRatingsShareASpamLimit()
    {
        assertThat(limitName("POST", "/api/feedback")).isEqualTo("feedback");
        assertThat(limitName("POST", "/api/ratings")).isEqualTo("feedback");
    }

    @Test
    public void otherWritesShareTheWriteLimit()
    {
        assertThat(limitName("POST", "/api/collection")).isEqualTo("writes");
        assertThat(limitName("POST", "/api/collection/batch")).isEqualTo("writes");
        assertThat(limitName("DELETE", "/api/collection/7")).isEqualTo("writes");
        assertThat(limitName("POST", "/api/rank")).isEqualTo("writes");
    }

    @Test
    public void readsShareTheGenerousReadLimit()
    {
        assertThat(limitName("GET", "/api/species")).isEqualTo("reads");
        assertThat(limitName("GET", "/api/ratings/summary")).isEqualTo("reads");
    }

    @Test
    public void pagesAndFilesAreNotLimited()
    {   // the app shell, assets and Google login aren't the expensive part
        assertThat(limitName("GET", "/team-builder")).isEqualTo("none");
        assertThat(limitName("GET", "/assets/index-abc.js")).isEqualTo("none");
        assertThat(limitName("GET", "/oauth2/authorization/google")).isEqualTo("none");
    }
}

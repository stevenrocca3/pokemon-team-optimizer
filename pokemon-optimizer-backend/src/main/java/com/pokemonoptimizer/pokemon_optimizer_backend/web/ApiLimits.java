package com.pokemonoptimizer.pokemon_optimizer_backend.web;

import java.time.Duration;
import java.util.Optional;

import com.pokemonoptimizer.pokemon_optimizer_backend.web.RateLimiter.Limit;

/**
 * Which rate limit an API request counts against, per client IP. Sized for one person using the app normally,
 * with room for a household or school sharing an IP: a burst, then a steady rate.
 */
public final class ApiLimits
{
    // ~5–15 s of every CPU core each: a burst of 8 builds, then one every 45 s
    static final Limit TEAM_BUILDER = new Limit("team-builder", 8, Duration.ofSeconds(45));
    // parsing a whole export: 10, then one every 30 s
    static final Limit IMPORT = new Limit("import", 10, Duration.ofSeconds(30));
    // nobody sends five messages or ratings in a row by hand: 5, then one every 5 minutes
    static final Limit FEEDBACK = new Limit("feedback", 5, Duration.ofMinutes(5));
    // saving Pokémon, removing them, IV rankings: 60, then one a second
    static final Limit WRITES = new Limit("writes", 60, Duration.ofSeconds(1));
    // species, moves and other cached lookups: 300, then five a second
    static final Limit READS = new Limit("reads", 300, Duration.ofMillis(200));

    private ApiLimits() {}

    public static Optional<Limit> forRequest(String method, String path)
    {
        if (!path.startsWith("/api/"))
        {
            return Optional.empty();
        }
        if (method.equals("GET") || method.equals("HEAD"))
        {
            return Optional.of(READS);
        }
        if (path.equals("/api/team-builder"))
        {
            return Optional.of(TEAM_BUILDER);
        }
        if (path.startsWith("/api/import/"))
        {
            return Optional.of(IMPORT);
        }
        if (path.equals("/api/feedback") || path.equals("/api/ratings"))
        {
            return Optional.of(FEEDBACK);
        }
        return Optional.of(WRITES);
    }
}

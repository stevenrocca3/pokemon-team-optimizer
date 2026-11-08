package com.pokemonoptimizer.pokemon_optimizer_backend.web;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token buckets per client: each Limit allows a burst of capacity, then one more request every refillEvery.
 * Kept in memory, so limits reset on restart and aren't shared between instances; fine for one small server.
 */
public final class RateLimiter
{
    public record Limit(String name, int capacity, Duration refillEvery) {}
    public record Decision(boolean allowed, long retryAfterSeconds) {}

    private final Clock clock;
    private final int maxClients;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock, int maxClients)
    {
        this.clock = clock;
        this.maxClients = maxClients;
    }

    /** How many client/limit pairs are being remembered. */
    public int trackedClients()
    {
        return buckets.size();
    }

    public Decision tryAcquire(Limit limit, String client)
    {
        long now = clock.millis();
        String key = limit.name() + "|" + client;
        if (!buckets.containsKey(key) && buckets.size() >= maxClients)
        {
            forgetFullBuckets(now);
        }
        return buckets.computeIfAbsent(key, k -> new Bucket(limit, now)).take(now);
    }

    /** Drops clients that have been idle long enough to be back at full capacity: forgetting them changes nothing. */
    private void forgetFullBuckets(long now)
    {
        buckets.values().removeIf(bucket -> bucket.isFull(now));
    }

    private static final class Bucket
    {
        private final int capacity;
        private final long refillMillis;
        private double tokens;
        private long updatedAt;

        Bucket(Limit limit, long now)
        {
            capacity = limit.capacity();
            refillMillis = limit.refillEvery().toMillis();
            tokens = capacity;
            updatedAt = now;
        }

        synchronized Decision take(long now)
        {
            refill(now);
            if (tokens >= 1)
            {
                tokens -= 1;
                return new Decision(true, 0);
            }
            long untilNextToken = (long) Math.ceil((1 - tokens) * refillMillis);
            return new Decision(false, Math.max(1, (untilNextToken + 999) / 1000));
        }

        synchronized boolean isFull(long now)
        {
            refill(now);
            return tokens >= capacity;
        }

        private void refill(long now)
        {
            tokens = Math.min(capacity, tokens + (double) (now - updatedAt) / refillMillis);
            updatedAt = now;
        }
    }
}

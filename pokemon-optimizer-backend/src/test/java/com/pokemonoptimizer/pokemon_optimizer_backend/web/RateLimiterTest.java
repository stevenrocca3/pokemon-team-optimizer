package com.pokemonoptimizer.pokemon_optimizer_backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.web.RateLimiter.Limit;

public class RateLimiterTest
{
    /** A clock the test moves by hand. */
    static class TestClock extends Clock
    {
        Instant now = Instant.parse("2026-11-08T12:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final TestClock clock = new TestClock();
    private final RateLimiter limiter = new RateLimiter(clock, 1000);
    private static final Limit THREE_PER_10S = new Limit("test", 3, Duration.ofSeconds(10));

    @Test
    public void allowsABurstUpToCapacityThenRefuses()
    {
        assertThat(limiter.tryAcquire(THREE_PER_10S, "1.1.1.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire(THREE_PER_10S, "1.1.1.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire(THREE_PER_10S, "1.1.1.1").allowed()).isTrue();

        RateLimiter.Decision fourth = limiter.tryAcquire(THREE_PER_10S, "1.1.1.1");
        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.retryAfterSeconds()).isEqualTo(10);
    }

    @Test
    public void oneRequestComesBackEachInterval()
    {
        for (int i = 0; i < 3; i++) limiter.tryAcquire(THREE_PER_10S, "1.1.1.1");

        clock.advance(Duration.ofSeconds(10));
        assertThat(limiter.tryAcquire(THREE_PER_10S, "1.1.1.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire(THREE_PER_10S, "1.1.1.1").allowed()).isFalse();
    }

    @Test
    public void retryAfterCountsDownThePartInterval()
    {   // 4 of the 10 seconds have passed, so 6 to go (rounded up, never 0)
        for (int i = 0; i < 3; i++) limiter.tryAcquire(THREE_PER_10S, "1.1.1.1");
        clock.advance(Duration.ofSeconds(4));

        assertThat(limiter.tryAcquire(THREE_PER_10S, "1.1.1.1").retryAfterSeconds()).isEqualTo(6);
    }

    @Test
    public void aLongPauseRefillsOnlyUpToCapacity()
    {   // an hour away doesn't bank 360 requests
        for (int i = 0; i < 3; i++) limiter.tryAcquire(THREE_PER_10S, "1.1.1.1");
        clock.advance(Duration.ofHours(1));

        for (int i = 0; i < 3; i++) assertThat(limiter.tryAcquire(THREE_PER_10S, "1.1.1.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire(THREE_PER_10S, "1.1.1.1").allowed()).isFalse();
    }

    @Test
    public void clientsHaveTheirOwnBuckets()
    {
        for (int i = 0; i < 3; i++) limiter.tryAcquire(THREE_PER_10S, "1.1.1.1");

        assertThat(limiter.tryAcquire(THREE_PER_10S, "2.2.2.2").allowed()).isTrue();
    }

    @Test
    public void limitsHaveTheirOwnBuckets()
    {   // using up the Team Builder limit mustn't block reading species
        for (int i = 0; i < 3; i++) limiter.tryAcquire(THREE_PER_10S, "1.1.1.1");

        assertThat(limiter.tryAcquire(new Limit("other", 3, Duration.ofSeconds(10)), "1.1.1.1").allowed()).isTrue();
    }

    @Test
    public void forgetsFullyRefilledClientsWhenItHitsItsMemoryCap()
    {   // memory stays bounded: idle clients whose buckets are full again are the ones dropped
        RateLimiter small = new RateLimiter(clock, 2);
        small.tryAcquire(THREE_PER_10S, "a");
        small.tryAcquire(THREE_PER_10S, "b");
        clock.advance(Duration.ofMinutes(1));

        assertThat(small.tryAcquire(THREE_PER_10S, "c").allowed()).isTrue();
        assertThat(small.trackedClients()).isLessThanOrEqualTo(2);
    }
}

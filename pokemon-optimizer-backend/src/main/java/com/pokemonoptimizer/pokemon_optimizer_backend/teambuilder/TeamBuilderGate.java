package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lets only a few Team Builder searches run at once. Each one already spreads its battles over every CPU core,
 * so running more side by side only makes all of them slower. Extra requests wait in line for a while, then get
 * a 503 telling the browser when to try again, instead of piling up until the server runs out of memory.
 */
@Component
public class TeamBuilderGate
{
    private final Semaphore slots;
    private final Duration queueWait;

    public TeamBuilderGate(@Value("${app.team-builder.max-concurrent:2}") int maxConcurrent,
                           @Value("${app.team-builder.queue-wait:30s}") Duration queueWait)
    {
        this.slots = new Semaphore(maxConcurrent, true);   // fair: first in line goes first
        this.queueWait = queueWait;
    }

    public <T> T run(Supplier<T> search)
    {
        boolean acquired;
        try
        {
            acquired = slots.tryAcquire(queueWait.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new ServerBusyException(RETRY_AFTER_SECONDS);
        }
        if (!acquired)
        {
            throw new ServerBusyException(RETRY_AFTER_SECONDS);
        }
        try
        {
            return search.get();
        }
        finally
        {
            slots.release();   // even when the search throws, or the slot would be gone for good
        }
    }

    private static final long RETRY_AFTER_SECONDS = 15;
}

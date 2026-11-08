package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class TeamBuilderGateTest
{
    @Test
    public void runsTheSearchAndReturnsItsResult()
    {
        TeamBuilderGate gate = new TeamBuilderGate(1, Duration.ofMillis(100));

        assertThat(gate.run(() -> 42)).isEqualTo(42);
    }

    @Test
    public void whenEverySlotStaysBusyItsA503WithRetryAfter() throws Exception
    {
        TeamBuilderGate gate = new TeamBuilderGate(1, Duration.ofMillis(100));
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        Thread first = new Thread(() -> gate.run(() -> {
            running.countDown();
            await(finish);
            return 1;
        }));
        first.start();
        assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();

        try
        {
            assertThatThrownBy(() -> gate.run(() -> 2))
                .isInstanceOfSatisfying(ServerBusyException.class, busy -> {
                    assertThat(busy.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(busy.getHeaders().getFirst("Retry-After")).isNotBlank();
                });
        }
        finally
        {
            finish.countDown();
            first.join();
        }
    }

    @Test
    public void aSlotFreesUpAfterASearchThrows()
    {   // a failed search mustn't leave its slot taken forever
        TeamBuilderGate gate = new TeamBuilderGate(1, Duration.ofMillis(100));

        assertThatThrownBy(() -> gate.run(() -> { throw new IllegalStateException("boom"); }))
            .isInstanceOf(IllegalStateException.class);
        assertThat(gate.run(() -> 3)).isEqualTo(3);
    }

    private static void await(CountDownLatch latch)
    {
        try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

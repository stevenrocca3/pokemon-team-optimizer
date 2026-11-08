package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class WinRateTest
{
    @Test
    public void wilsonIntervalAroundAnEvenRecord()
    {   // centre (0.5 + 1.96²/200) / (1 + 1.96²/100) = 0.5; half-width 1.96·√(0.0025 + 0.00009604) / 1.0384 = 0.0962
        WinRate rate = WinRate.of(50, 100);

        assertEquals(0.5, rate.rate(), 1e-9);
        assertEquals(0.4038, rate.low(), 1e-4);
        assertEquals(0.5962, rate.high(), 1e-4);
    }

    @Test
    public void wilsonIntervalStaysInsideZeroToOne()
    {   // a normal-approximation interval would go below 0 here; Wilson's doesn't
        WinRate rate = WinRate.of(1, 20);

        assertEquals(0.05, rate.rate(), 1e-9);
        assertEquals(0.0089, rate.low(), 1e-4);
        assertEquals(0.2361, rate.high(), 1e-4);
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

public class TypeChartTest 
{
    
    private static final double SE = 1.60000002384185791015625;
    private static final double RESISTED = 0.625;
    private static final double IMMUNE = 0.390625;

    @Test 
    public void neutralIsOne()
    {
        assertEquals(1.0, TypeChart.effectiveness("fire", List.of("normal")), 0.0);
    }

    @Test
    public void superEffective()
    {
        assertEquals(SE, TypeChart.effectiveness("fighting", List.of("normal")), 0.0);
    }

    @Test
    public void resisted()
    {
        assertEquals(RESISTED, TypeChart.effectiveness("fighting", List.of("psychic")), 0.0);
    }

    @Test
    public void immunityIsDoubleResistedNotZero()
    {
        assertEquals(IMMUNE, TypeChart.effectiveness("fighting", List.of("ghost")), 0.0);
    }

    @Test
    public void dualTypeWeaknessesStack()
    {   // Probopass-style rock/steel takes fighting at 1.6 x 1.6
        assertEquals(SE * SE, TypeChart.effectiveness("fighting", List.of("rock", "steel")), 0.0);
    }

    @Test
    public void weaknessAndResistanceDoNotCancelExactly()
    {   // normal/flying vs fighting: 1.60000002 x 0.625 = 1.0000000149, not 1.0
        assertEquals(SE * RESISTED, TypeChart.effectiveness("fighting", List.of("normal", "flying")), 0.0);
    }
}

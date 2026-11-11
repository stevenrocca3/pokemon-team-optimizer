package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.PowerUpCost;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.EvolutionStep;

public class EvolutionCostsTest
{
    private static final EvolutionCosts COSTS = new EvolutionCosts();

    @Test
    public void aPlainEvolutionIsJustCandy()
    {
        assertEquals(Optional.of(new EvolutionStep("marshtomp", "swampert", 100, null, 0, null, List.of())), COSTS.step("marshtomp", "swampert"));
    }

    @Test
    public void itemsLuresAndConditionsComeThrough()
    {
        assertEquals("Sinnoh Stone", COSTS.step("roselia", "roserade").orElseThrow().item());
        assertEquals("Magnetic Lure Module", COSTS.step("magneton", "magnezone").orElseThrow().lure());
        assertEquals(List.of("Walk 10 km as your buddy", "Evolve at night"), COSTS.step("eevee", "umbreon").orElseThrow().conditions());
    }

    @Test
    public void gholdengoTakesCoinsNotCandy()
    {
        EvolutionStep gholdengo = COSTS.step("gimmighoul", "gholdengo").orElseThrow();
        assertEquals(0, gholdengo.candy());
        assertEquals("Gimmighoul Coin", gholdengo.item());
        assertEquals(999, gholdengo.itemCount());
    }

    @Test
    public void anEvolutionTheGameDoesntHaveIsEmpty()
    {   // Scyther -> Kleavor isn't in Pokémon GO's data
        assertTrue(COSTS.step("scyther", "kleavor").isEmpty());
        assertTrue(COSTS.step("mudkip", "swampert").isEmpty());   // two steps, not one
    }

    @Test
    public void theSecondMoveCostFollowsTheBuddyDistance()
    {
        assertEquals(Optional.of(new PowerUpCost.Cost(10000, 25, 0)), COSTS.secondMove("swampert"));
        assertEquals(Optional.of(new PowerUpCost.Cost(50000, 50, 0)), COSTS.secondMove("medicham"));
        assertTrue(COSTS.secondMove("missingno").isEmpty());
    }
}

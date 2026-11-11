package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMovesets;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.BuildCost;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.EvolutionStep;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.EvolvedPokemon;
import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.PowerUpCost;

public class EvolvableCandidatesTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final EvolvableCandidates EVOLVABLE = new EvolvableCandidates(new EvolutionTree(SPECIES),
        new EvolutionMoves(SPECIES, new MoveLookup(), new SuggestedMovesets(SPECIES)), new EvolutionCosts(), SPECIES);

    private static final CaughtPokemonRequest SWAMPERT = new CaughtPokemonRequest("swampert", 1, 15, 15, 22.5, "WATER_GUN", List.of("SURF"), false);
    private static final CaughtPokemonRequest MUDKIP = new CaughtPokemonRequest("mudkip", 0, 15, 14, 18.0, "TACKLE", List.of("DIG"), true);

    private static EvolvedPokemon form(List<EvolvedPokemon> evolved, String speciesId)
    {
        return evolved.stream().filter(e -> e.pokemon().speciesId().equals(speciesId)).findFirst().orElseThrow();
    }

    @Test
    public void anUnevolvedPokemonComesBackAsEachFormItEvolvesInto()
    {   // Mudkip is second in the request, so both forms point back to index 1
        List<EvolvedPokemon> evolved = EVOLVABLE.expand(List.of(SWAMPERT, MUDKIP));

        assertEquals(List.of("marshtomp", "swampert"), evolved.stream().map(e -> e.pokemon().speciesId()).toList());
        assertTrue(evolved.stream().allMatch(e -> e.index() == 1 && e.evolution().fromSpeciesId().equals("mudkip")));
    }

    @Test
    public void evolvingKeepsItsIvsLevelAndShadowButTakesTheFormsBestMoves()
    {   // the Mudkip's Tackle and Dig don't survive evolving; PvPoke's Swampert set does
        CaughtPokemonRequest swampert = form(EVOLVABLE.expand(List.of(MUDKIP)), "swampert").pokemon();

        assertEquals(List.of(0, 15, 14), List.of(swampert.atkIv(), swampert.defIv(), swampert.staIv()));
        assertEquals(18.0, swampert.level());
        assertTrue(swampert.shadow());
        assertEquals("MUD_SHOT", swampert.fastMove());
        assertEquals(List.of("HYDRO_CANNON", "EARTHQUAKE"), swampert.chargedMoves());
    }

    @Test
    public void aFormWhoseBestMovesAreExclusiveCarriesTheWarning()
    {
        EvolvedPokemon swampert = form(EVOLVABLE.expand(List.of(MUDKIP)), "swampert");
        EvolvedPokemon marshtomp = form(EVOLVABLE.expand(List.of(MUDKIP)), "marshtomp");

        assertEquals(1, swampert.evolution().moveWarnings().size());
        assertTrue(swampert.evolution().moveWarnings().get(0).startsWith("Swampert needs Hydro Cannon"));
        assertEquals(List.of(), marshtomp.evolution().moveWarnings());
    }

    @Test
    public void aFullyEvolvedOrUnknownPokemonAddsNothing()
    {
        CaughtPokemonRequest unknown = new CaughtPokemonRequest("missingno", 0, 0, 0, null, "TACKLE", List.of("DIG"), false);

        assertEquals(List.of(), EVOLVABLE.expand(List.of(SWAMPERT, unknown)));
    }

    // #139: what evolving it takes, step by step, and its second Charged Move
    @Test
    public void theBuildCostAddsUpEachEvolutionsCandy()
    {   // Mudkip -> Marshtomp is 25 Candy, Marshtomp -> Swampert 100
        BuildCost cost = form(EVOLVABLE.expand(List.of(new CaughtPokemonRequest("mudkip", 0, 15, 14, 18.0, "TACKLE", List.of("DIG"), false))), "swampert").evolution().cost();

        assertEquals("FAMILY_MUDKIP", cost.family());
        assertEquals(List.of("mudkip", "marshtomp"), cost.steps().stream().map(EvolutionStep::from).toList());
        assertEquals(List.of(25, 100), cost.steps().stream().map(EvolutionStep::candy).toList());
        assertTrue(cost.complete());
        assertEquals(125, cost.evolveCandy());
        assertEquals(new PowerUpCost.Cost(10000, 25, 0), cost.secondMove());
    }

    @Test
    public void aShadowsSecondMoveCostsMore()
    {   // GO's shadowThirdMoveUnlock multipliers are 1.2 for both
        BuildCost cost = form(EVOLVABLE.expand(List.of(MUDKIP)), "swampert").evolution().cost();

        assertEquals(new PowerUpCost.Cost(12000, 30, 0), cost.secondMove());
    }

    @Test
    public void anItemEvolutionNamesTheItem()
    {   // Politoed takes a King's Rock as well as the Candy
        CaughtPokemonRequest poliwhirl = new CaughtPokemonRequest("poliwhirl", 1, 15, 15, 20.0, "BUBBLE", List.of("SCALD"), false);
        EvolutionStep step = form(EVOLVABLE.expand(List.of(poliwhirl)), "politoed").evolution().cost().steps().get(0);

        assertEquals(100, step.candy());
        assertEquals("King's Rock", step.item());
        assertEquals(1, step.itemCount());
    }
}


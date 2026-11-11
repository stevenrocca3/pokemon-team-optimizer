package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMovesets;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.EvolvedPokemon;

public class EvolvableCandidatesTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final EvolvableCandidates EVOLVABLE = new EvolvableCandidates(new EvolutionTree(SPECIES),
        new EvolutionMoves(SPECIES, new MoveLookup(), new SuggestedMovesets(SPECIES)));

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
}

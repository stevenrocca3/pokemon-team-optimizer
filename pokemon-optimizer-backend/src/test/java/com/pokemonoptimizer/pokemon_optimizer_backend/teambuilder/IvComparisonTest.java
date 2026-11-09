package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMovesets;

public class IvComparisonTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();
    private static final MetaLookup META = new MetaLookup(SPECIES, MOVES);
    private static final IvComparison COMPARISON = new IvComparison(
        new TeamBuilder(SPECIES, MOVES, META, new GoodIvs(SPECIES, new SuggestedMovesets(SPECIES))), META);

    private static CaughtPokemonRequest medicham(int atk, int def, int sta, double level)
    {
        return new CaughtPokemonRequest("medicham", atk, def, sta, level, "COUNTER", List.of("ICE_PUNCH", "PSYCHIC"), false);
    }

    private static final CaughtPokemonRequest POOR = medicham(15, 0, 0, 20);
    private static final CaughtPokemonRequest GOOD = medicham(0, 15, 15, 20);

    @Test
    public void theBetterSpreadComesFirstBuiltForTheLeague()
    {
        IvComparisonResult result = COMPARISON.compare(League.GREAT, List.of(POOR, GOOD));

        ComparedPokemon best = result.pokemon().get(0);
        assertEquals(1, best.index());
        assertEquals(50.0, best.level());   // 0/15/15 Medicham: level 50 at 1439 CP
        assertEquals(1439, best.cp());
        assertEquals(44 * 3, best.battles());
        assertTrue(best.wins() > result.pokemon().get(1).wins(), result.pokemon().toString());
        assertEquals(44, result.metaSize());
    }

    @Test
    public void flipsAccountForTheWholeDifferenceInWins()
    {   // every win one has that the other doesn't is a flip, and nothing else is
        IvComparisonResult result = COMPARISON.compare(League.GREAT, List.of(POOR, GOOD));

        int onlyGood = 0;
        int onlyPoor = 0;
        for (FlippedMatchup flip : result.flips())
        {
            assertEquals(1, flip.winners().size(), flip.toString());
            if (flip.winners().contains(1)) onlyGood++;
            else onlyPoor++;
        }
        ComparedPokemon good = result.pokemon().stream().filter(p -> p.index() == 1).findFirst().orElseThrow();
        ComparedPokemon poor = result.pokemon().stream().filter(p -> p.index() == 0).findFirst().orElseThrow();
        assertTrue(onlyGood > 0);
        assertEquals(good.wins() - poor.wins(), onlyGood - onlyPoor);
    }

    @Test
    public void identicalCopiesNeverDiffer()
    {
        IvComparisonResult result = COMPARISON.compare(League.GREAT, List.of(GOOD, medicham(0, 15, 15, 30)));

        assertEquals(2, result.pokemon().size());
        assertTrue(result.pokemon().get(0).wins() > 0);
        assertEquals(result.pokemon().get(0).wins(), result.pokemon().get(1).wins());
        assertEquals(List.of(), result.flips());
    }

    @Test
    public void oneThatCantPlaySaysWhyAndIsLeftOutOfTheFlips()
    {   // 15/15/15 Medicham at level 50 is over 1500 CP: it can't be powered down
        IvComparisonResult result = COMPARISON.compare(League.GREAT, List.of(medicham(15, 15, 15, 50), GOOD, POOR));

        ComparedPokemon overCap = result.pokemon().stream().filter(p -> p.index() == 0).findFirst().orElseThrow();
        assertNotNull(overCap.problem());
        assertEquals(0, overCap.wins());
        assertEquals(result.pokemon().get(2), overCap);   // the ones that can play come first
        assertNull(result.pokemon().get(0).problem());
        assertTrue(result.flips().stream().noneMatch(f -> f.winners().contains(0)));
    }
}

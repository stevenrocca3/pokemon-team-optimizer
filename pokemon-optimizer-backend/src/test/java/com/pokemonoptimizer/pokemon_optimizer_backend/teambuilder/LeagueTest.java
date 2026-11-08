package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

public class LeagueTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final SpeciesType SWAMPERT = SPECIES.findById("swampert").orElseThrow();
    private static final SpeciesType MEDICHAM = SPECIES.findById("medicham").orElseThrow();
    private static final SpeciesType REGISTEEL = SPECIES.findById("registeel").orElseThrow();

    @Test
    public void buildsToTheHighestLevelUnderTheCap()
    {   // 0/15/14 Swampert: CP fits 1500 up to level 18.5, 2500 up to 33
        assertEquals(OptionalDouble.of(18.5), League.GREAT.buildLevel(SWAMPERT, 0, 15, 14, 15));
        assertEquals(OptionalDouble.of(33.0), League.ULTRA.buildLevel(SWAMPERT, 0, 15, 14, 15));
    }

    @Test
    public void neverBuildsPastLevel50()
    {   // 0/15/15 Medicham is only 1439 CP at 50
        assertEquals(OptionalDouble.of(50.0), League.GREAT.buildLevel(MEDICHAM, 0, 15, 15, 20));
        assertEquals(OptionalDouble.of(50.0), League.MASTER.buildLevel(MEDICHAM, 0, 15, 15, 20));
    }

    @Test
    public void keepsABestBuddyAboveFifty()
    {   // level 51 is reachable only as a Best Buddy; building can't take that away
        assertEquals(OptionalDouble.of(51.0), League.MASTER.buildLevel(MEDICHAM, 0, 15, 15, 51));
    }

    @Test
    public void alreadyOverTheCapIsNotEligible()
    {   // 10/10/10 Registeel at 40 is 2321 CP; powering down isn't possible
        assertEquals(OptionalDouble.empty(), League.GREAT.buildLevel(REGISTEEL, 10, 10, 10, 40));
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

/** Expected numbers worked out by hand from the CP formula, PvPoke's CPM table and the gamemaster's base stats. */
public class EvolutionProjectorTest
{
    private static final SpeciesLookup species = new SpeciesLookup();
    private static final EvolutionProjector projector = new EvolutionProjector(species, new EvolutionTree(species),
        new EvolutionMoves(species, new com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup(),
            new com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMovesets(species)));

    private static FormProjection form(EvolutionProjection p, String id)
    {
        return p.forms().stream().filter(f -> f.speciesId().equals(id)).findFirst().orElseThrow();
    }

    private static LeagueProjection league(FormProjection f, League league)
    {
        return f.leagues().stream().filter(l -> l.league() == league).findFirst().orElseThrow();
    }

    // the issue's example: a level 15 Shadow Dratini, 0/14/12
    private static final EvolutionProjection DRATINI = projector.project("dratini", 0, 14, 12, 15.0, true);

    @Test
    public void projectsEveryLaterStage()
    {
        assertEquals("Dratini", DRATINI.name());
        assertTrue(DRATINI.shadow());
        assertEquals(List.of("dragonair", "dragonite"), DRATINI.forms().stream().map(FormProjection::speciesId).toList());
        assertEquals(List.of(League.GREAT, League.ULTRA, League.MASTER),
            form(DRATINI, "dragonite").leagues().stream().map(LeagueProjection::league).toList());
    }

    @Test
    public void dragonairPowersUpToTheHighestLevelUnderGreatLeaguesCap()
    {
        LeagueProjection great = league(form(DRATINI, "dragonair"), League.GREAT);
        assertTrue(great.eligible());
        assertEquals(35.0, great.level());
        assertEquals(1495, great.cp());
        assertEquals(46, great.statProductRank());
        assertEquals(98.72, great.percentOfBest(), 0.005);
        assertFalse(great.needsXlCandy());
    }

    @Test
    public void dragoniteFitsUltraLeagueAtLevel24AndAHalf()
    {
        LeagueProjection ultra = league(form(DRATINI, "dragonite"), League.ULTRA);
        assertEquals(24.5, ultra.level());
        assertEquals(2488, ultra.cp());
        assertEquals(12, ultra.statProductRank());
        assertEquals(99.12, ultra.percentOfBest(), 0.005);
    }

    @Test
    public void masterLeagueIsLevel50WithXlCandyAndABestBuddyCp()
    {
        LeagueProjection master = league(form(DRATINI, "dragonite"), League.MASTER);
        assertEquals(50.0, master.level());
        assertEquals(4019, master.cp());
        assertTrue(master.needsXlCandy());
        assertEquals(4067, master.bestBuddyCp());   // level 51
        assertEquals(1099, master.statProductRank());
    }

    @Test
    public void aFormAlreadyOverTheCapAtItsLevelIsMarkedIneligible()
    {   // evolved at level 15, this Dragonite is 1523 CP: too strong for Great League, and levels never go down
        LeagueProjection great = league(form(DRATINI, "dragonite"), League.GREAT);
        assertFalse(great.eligible());
        assertNull(great.level());
        assertNull(great.cp());
    }

    @Test
    public void withoutAKnownLevelItProjectsFromLevelOne()
    {   // no CP entered: level 1 is the lowest it could be, so Dragonite gets into Great League
        LeagueProjection great = league(form(projector.project("dratini", 0, 14, 12, null, false), "dragonite"), League.GREAT);
        assertTrue(great.eligible());
        assertTrue(great.cp() <= 1500);
    }

    @Test
    public void aSpeciesThatDoesntEvolveHasNoForms()
    {
        assertEquals(List.of(), projector.project("dragonite", 15, 15, 15, 20.0, false).forms());
    }
}

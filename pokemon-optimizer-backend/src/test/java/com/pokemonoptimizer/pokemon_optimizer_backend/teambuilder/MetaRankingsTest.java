package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

public class MetaRankingsTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MetaRankings RANKINGS = new MetaRankings(SPECIES);

    // Great and Ultra show PvPoke's top 150, Master its top 50, each Pokémon once
    @Test
    public void top150ForGreatAndUltraAnd50ForMaster()
    {
        assertEquals(150, RANKINGS.top(League.GREAT).size());
        assertEquals(150, RANKINGS.top(League.ULTRA).size());
        assertEquals(50, RANKINGS.top(League.MASTER).size());
        for (League league : League.values())
        {
            List<MetaPick> top = RANKINGS.top(league);
            assertEquals(top.size(), top.stream().map(p -> p.speciesId() + "/" + p.shadow()).distinct().count(), league + " repeats one");
        }
    }

    // In rank order, with Shadows split out: Great starts Melmetal, Altaria, Shadow Ninetales (rankings-1500.json)
    @Test
    public void inRankOrderWithShadowsSplitOut()
    {
        List<MetaPick> great = RANKINGS.top(League.GREAT);

        assertEquals(new MetaPick("melmetal", false, List.of("steel")), great.get(0));
        assertEquals("altaria", great.get(1).speciesId());
        assertEquals("ninetales", great.get(2).speciesId());
        assertTrue(great.get(2).shadow());
        assertEquals("palkia_origin", RANKINGS.top(League.MASTER).get(0).speciesId());
        assertEquals("tinkaton", RANKINGS.top(League.ULTRA).get(0).speciesId());
    }

    // Every one is a known species with its types, so the grid can show its icon and type badges
    @Test
    public void everyOneIsAKnownSpeciesWithItsTypes()
    {
        for (League league : League.values())
        {
            for (MetaPick pick : RANKINGS.top(league))
            {
                assertEquals(SPECIES.findById(pick.speciesId()).orElseThrow().types().stream().filter(t -> !"none".equals(t)).toList(),
                    pick.types(), pick.speciesId());
                assertFalse(pick.types().isEmpty(), pick.speciesId());
            }
        }
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

public class EvolutionTreeTest
{
    private static final EvolutionTree tree = new EvolutionTree(new SpeciesLookup());

    private static List<String> ids(List<EvolvedForm> forms)
    {
        return forms.stream().map(EvolvedForm::speciesId).toList();
    }

    @Test
    public void aThreeStageLineListsBothLaterStagesWithTheirPath()
    {
        List<EvolvedForm> forms = tree.evolutionsOf("dratini");
        assertEquals(List.of(new EvolvedForm("dragonair", 1, List.of("dratini", "dragonair")),
                             new EvolvedForm("dragonite", 2, List.of("dratini", "dragonair", "dragonite"))), forms);
    }

    @Test
    public void aMiddleStageOnlyLooksForward()
    {
        assertEquals(List.of("swampert"), ids(tree.evolutionsOf("marshtomp")));
    }

    @Test
    public void aFinalStageHasNothingToEvolveInto()
    {
        assertEquals(List.of(), tree.evolutionsOf("dragonite"));
    }

    @Test
    public void branchesKeepEveryBranch()
    {   // Eevee's eight, Tyrogue's three, and Wurmple's two branches that each evolve once more
        assertEquals(8, tree.evolutionsOf("eevee").size());
        assertEquals(List.of("hitmonlee", "hitmonchan", "hitmontop"), ids(tree.evolutionsOf("tyrogue")));
        assertEquals(List.of("silcoon", "cascoon", "beautifly", "dustox"), ids(tree.evolutionsOf("wurmple")));
        assertEquals(List.of("wurmple", "cascoon", "dustox"), tree.evolutionsOf("wurmple").get(3).path());
    }

    @Test
    public void regionalFormsFollowTheirOwnLine()
    {
        assertEquals(List.of("slowbro_galarian", "slowking_galarian"), ids(tree.evolutionsOf("slowpoke_galarian")));
        assertEquals(List.of("linoone_galarian", "obstagoon"), ids(tree.evolutionsOf("zigzagoon_galarian")));
        assertEquals(List.of("slowbro", "slowking"), ids(tree.evolutionsOf("slowpoke")));
    }

    @Test
    public void aMissingEvolutionIdIsSkipped()
    {   // PvPoke's data points Rockruff at "lycranroc_dusk", which doesn't exist; the real Lycanroc forms still come through
        List<String> rockruff = ids(tree.evolutionsOf("rockruff"));
        assertTrue(rockruff.stream().noneMatch(id -> id.equals("lycranroc_dusk")), rockruff.toString());
        assertTrue(rockruff.contains("lycanroc_midday"), rockruff.toString());
    }

    @Test
    public void anUnreleasedMiddleStageStillLeadsToTheReleasedOneAfterIt()
    {   // Spewpa is unreleased in PvPoke's data, Vivillon isn't: Scatterbug still shows Vivillon, two stages on
        List<EvolvedForm> forms = tree.evolutionsOf("scatterbug");
        assertTrue(ids(forms).stream().noneMatch(id -> id.equals("spewpa")), ids(forms).toString());
        EvolvedForm vivillon = forms.stream().filter(f -> f.speciesId().startsWith("vivillon")).findFirst().orElseThrow();
        assertEquals(2, vivillon.stage());
        assertEquals("spewpa", vivillon.path().get(1));
    }

    @Test
    public void unknownSpeciesHaveNoEvolutions()
    {
        assertEquals(List.of(), tree.evolutionsOf("missingno"));
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.cups.Cup;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.CupCatalog;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

public class MetaPresetsTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();
    private static final MetaLookup META = new MetaLookup(SPECIES, MOVES);

    private static MetaPokemon metaEntry(League league, String id)
    {
        return META.meta(league).stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private static void assertIsTheMetaBuild(MetaPokemon expected, CaughtPokemonRequest preset)
    {
        assertEquals(expected.combatant().species().speciesId(), preset.speciesId());
        assertEquals(expected.combatant().shadow(), preset.shadow());
        assertEquals(expected.combatant().atkIv(), preset.atkIv());
        assertEquals(expected.combatant().defIv(), preset.defIv());
        assertEquals(expected.combatant().staIv(), preset.staIv());
        assertEquals(expected.combatant().level(), preset.level());
        assertEquals(expected.moveset().fastMove().moveId(), preset.fastMove());
        assertEquals(expected.moveset().chargedMoves().stream().map(Move::moveId).toList(), preset.chargedMoves());
    }

    // A League's meta Pokémon comes back as PvPoke builds it there: its default IVs and level, and its meta moves
    @Test
    public void aMetaPokemonIsItsLeagueBuild()
    {
        MetaPresets presets = new MetaPresets(META, mock(CupCatalog.class));
        MetaPokemon medicham = META.meta(League.GREAT).stream().filter(p -> p.combatant().species().speciesId().equals("medicham")).findFirst().orElseThrow();

        assertIsTheMetaBuild(medicham, presets.preset(League.GREAT, null, "medicham", medicham.combatant().shadow()).orElseThrow());
    }

    // The Shadow is its own meta entry, with its own IVs; asking for it gets that one, not the regular form
    @Test
    public void aShadowIsTheShadowsEntry()
    {
        MetaPresets presets = new MetaPresets(META, mock(CupCatalog.class));

        assertIsTheMetaBuild(metaEntry(League.GREAT, "annihilape_shadow"),
            presets.preset(League.GREAT, null, "annihilape", true).orElseThrow());
    }

    // Only Pokémon in that meta have a preset: Magikarp doesn't, and neither does a meta species in the wrong League
    @Test
    public void nothingOutsideTheMeta()
    {
        MetaPresets presets = new MetaPresets(META, mock(CupCatalog.class));

        assertTrue(presets.preset(League.GREAT, null, "magikarp", false).isEmpty());
        String greatOnly = META.meta(League.GREAT).stream()
            .filter(g -> META.meta(League.MASTER).stream().noneMatch(m -> m.id().equals(g.id())))
            .findFirst().orElseThrow().combatant().species().speciesId();
        assertTrue(presets.preset(League.MASTER, null, greatOnly, false).isEmpty(), greatOnly + " isn't Master League meta");
    }

    // A suggestion from a Cup comes from that Cup's meta
    @Test
    public void aCupUsesItsOwnMeta()
    {
        MetaPokemon altaria = metaEntry(League.GREAT, "altaria");
        CupCatalog cups = mock(CupCatalog.class);
        when(cups.find("fantasy-1500")).thenReturn(Optional.of(new Cup("fantasy-1500", "Fantasy Cup", League.GREAT, 1500, 3, List.of(),
            null, List.of(altaria), "2026-11-03")));
        MetaPresets presets = new MetaPresets(META, cups);

        assertIsTheMetaBuild(altaria, presets.preset(League.GREAT, "fantasy-1500", "altaria", false).orElseThrow());
        assertTrue(presets.preset(League.GREAT, "fantasy-1500", "medicham", false).isEmpty(), "Medicham isn't in this Cup's meta");
        assertTrue(presets.preset(League.GREAT, "ended-cup", "altaria", false).isEmpty(), "no such Cup");
    }
}

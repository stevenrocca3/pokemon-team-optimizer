package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Combatant;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

public class MetaLookupTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MetaLookup META = new MetaLookup(SPECIES, new MoveLookup());

    private static MetaPokemon find(League league, String id)
    {
        return META.meta(league).stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    public void everyLeagueHasItsWholeMetaGroup()
    {
        assertEquals(44, META.meta(League.GREAT).size());
        assertEquals(45, META.meta(League.ULTRA).size());
        assertEquals(32, META.meta(League.MASTER).size());
    }

    @Test
    public void metaPokemonUsePvPokesDefaultIvsAndMoves()
    {   // gamemaster altaria.defaultIVs.cp1500 = [28.5, 4, 12, 13]; meta moves Dragon Breath / Moonblast, Flamethrower
        MetaPokemon altaria = find(League.GREAT, "altaria");

        assertEquals(new Combatant(SPECIES.findById("altaria").orElseThrow(), 4, 12, 13, 28.5, false), altaria.combatant());
        assertEquals("DRAGON_BREATH", altaria.moveset().fastMove().moveId());
        assertEquals(List.of("MOONBLAST", "FLAMETHROWER"), altaria.moveset().chargedMoves().stream().map(Move::moveId).toList());
    }

    @Test
    public void shadowEntriesAreTheBaseSpeciesAsShadow()
    {   // our species data has no *_shadow entries (ADR 0004): Shadow is a flag on the battler
        Combatant annihilape = find(League.GREAT, "annihilape_shadow").combatant();

        assertEquals("annihilape", annihilape.species().speciesId());
        assertEquals(true, annihilape.shadow());
        assertEquals(17.0, annihilape.level());
    }

    @Test
    public void masterLeagueMetaIsLevel50Hundos()
    {   // PvPoke has no cp10000 defaults; Master League battles at 50 with 15/15/15
        Combatant dialga = find(League.MASTER, "dialga_origin").combatant();

        assertEquals(50.0, dialga.level());
        assertEquals(List.of(15, 15, 15), List.of(dialga.atkIv(), dialga.defIv(), dialga.staIv()));
    }

    @Test
    public void snapshotDateComesFromTheGamemaster()
    {   // gamemaster.json "timestamp": "2026-10-04 00:17:21"; results show which meta they were built against
        assertEquals("2026-10-04", META.snapshotDate());
    }
}

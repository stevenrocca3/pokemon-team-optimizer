package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMovesets;

public class EvolutionMovesTest
{
    private static final SpeciesLookup species = new SpeciesLookup();
    private static final EvolutionMoves moves = new EvolutionMoves(species, new MoveLookup(), new SuggestedMovesets(species));

    private static MoveOption option(List<MoveOption> pool, String id)
    {
        return pool.stream().filter(m -> m.moveId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    public void theMovepoolTagsCommunityDayMovesAsExclusive()
    {
        FormMoves swampert = moves.forForm("swampert").orElseThrow();
        MoveOption hydroCannon = option(swampert.movepool().chargedMoves(), "HYDRO_CANNON");
        assertEquals("Hydro Cannon", hydroCannon.name());
        assertTrue(hydroCannon.exclusive());
        assertFalse(hydroCannon.legacy());
        assertFalse(option(swampert.movepool().chargedMoves(), "EARTHQUAKE").exclusive());
        assertFalse(option(swampert.movepool().fastMoves(), "MUD_SHOT").exclusive());
    }

    @Test
    public void legacyMovesAreExclusiveAndTaggedLegacy()
    {   // Porygon's Quick Attack is in PvPoke's legacyMoves
        MoveOption quickAttack = option(moves.forForm("porygon").orElseThrow().movepool().fastMoves(), "QUICK_ATTACK");
        assertTrue(quickAttack.exclusive());
        assertTrue(quickAttack.legacy());
    }

    @Test
    public void anExclusiveMoveInTheBestMovesetGetsAWarning()
    {
        FormMoves swampert = moves.forForm("swampert").orElseThrow();
        assertEquals("MUD_SHOT", swampert.optimalFastMove());
        assertEquals(List.of("HYDRO_CANNON", "EARTHQUAKE"), swampert.optimalChargedMoves());
        assertEquals(1, swampert.warnings().size());
        MoveWarning warning = swampert.warnings().get(0);
        assertEquals("HYDRO_CANNON", warning.moveId());
        assertFalse(warning.fast());
        assertEquals("Swampert needs Hydro Cannon: an Elite Charged TM, or evolving it during an event that has the move, like its Community Day.",
            warning.message());
    }

    @Test
    public void fastAndChargedExclusivesNameTheirOwnTm()
    {   // Charizard's best set needs Dragon Breath (Elite Fast TM) and Blast Burn (Elite Charged TM)
        List<MoveWarning> warnings = moves.forForm("charizard").orElseThrow().warnings();
        assertEquals(List.of("DRAGON_BREATH", "BLAST_BURN"), warnings.stream().map(MoveWarning::moveId).toList());
        assertTrue(warnings.get(0).fast());
        assertTrue(warnings.get(0).message().contains("an Elite Fast TM"), warnings.get(0).message());
        assertTrue(warnings.get(1).message().contains("an Elite Charged TM"), warnings.get(1).message());
    }

    @Test
    public void noWarningWhenTheBestMovesetIsAllRegularMoves()
    {   // Dragonite can learn exclusives (Draco Meteor), but its best set doesn't use them
        FormMoves dragonite = moves.forForm("dragonite").orElseThrow();
        assertTrue(option(dragonite.movepool().chargedMoves(), "DRACO_METEOR").exclusive());
        assertEquals(List.of(), dragonite.warnings());
    }

    @Test
    public void theProjectionCarriesEachFormsMovesAndWarnings()
    {   // the issue's acceptance: a Mudkip's Swampert says it needs an Elite TM or event for its best moveset
        EvolutionProjector projector = new EvolutionProjector(species, new EvolutionTree(species), moves);
        EvolutionProjection mudkip = projector.project("mudkip", 0, 15, 15, 20.0, false);
        FormProjection swampert = mudkip.forms().stream().filter(f -> f.speciesId().equals("swampert")).findFirst().orElseThrow();
        assertEquals("HYDRO_CANNON", swampert.moves().warnings().get(0).moveId());
        FormProjection marshtomp = mudkip.forms().get(0);
        assertEquals(List.of(), marshtomp.moves().warnings());
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

public class SuggestedMovesetsTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final SuggestedMovesets SUGGESTED = new SuggestedMovesets(SPECIES);

    @Test
    public void medichamGetsPvPokesMoveset()
    {   // PvPoke's Great League pick at the e02bde7 snapshot
        assertEquals(Optional.of(new SuggestedMoves("PSYCHO_CUT", List.of("ICE_PUNCH", "DYNAMIC_PUNCH"))), SUGGESTED.forSpecies("medicham"));
    }

    @Test
    public void aSpeciesPvPokeDoesntRankHasNoSuggestion()
    {
        assertEquals(Optional.empty(), SUGGESTED.forSpecies("charmander"));
    }

    @Test
    public void everySuggestionIsLegalForItsSpecies()
    {
        for (SpeciesType species : SPECIES.findAll())
        {
            SUGGESTED.forSpecies(species.speciesId()).ifPresent(moves -> assertTrue(
                species.learnset().allows(moves.fastMove(), moves.chargedMoves()), species.speciesId() + " " + moves));
        }
    }
}

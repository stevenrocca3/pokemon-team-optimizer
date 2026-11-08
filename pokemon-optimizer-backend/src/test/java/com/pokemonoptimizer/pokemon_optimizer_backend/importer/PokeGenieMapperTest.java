package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

public class PokeGenieMapperTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private final PokeGenieMapper mapper = new PokeGenieMapper(SPECIES, new MoveLookup());

    private static PokeGenieRow row(String name, String form, String fast, String charged1, String charged2, boolean shadow)
    {
        return new PokeGenieRow(1, name, form, 0, 15, 14, 20.0, fast, charged1, charged2, shadow);
    }

    private ImportPreview mapOne(PokeGenieRow row)
    {
        return mapper.map(new PokeGenieImport(List.of(row), List.of()));
    }

    @Test
    public void mapsNamesAndMovesToOurIds()
    {
        ImportPreview preview = mapOne(row("Swampert", "", "Mud Shot", "Hydro Cannon", "Earthquake", true));

        assertEquals(List.of(new ImportedPokemon(1, "swampert", 0, 15, 14, 20.0, true,
            "MUD_SHOT", List.of("HYDRO_CANNON", "EARTHQUAKE"), false)), preview.pokemon());
    }

    @Test
    public void regionalFormMapsToTheRegionalSpecies()
    {   // Genie: Name "Stunfisk", Form "Galar"; PvPoke: stunfisk_galarian
        ImportPreview preview = mapOne(row("Stunfisk", "Galar", "Mud Shot", "Rock Slide", "", false));

        assertEquals("stunfisk_galarian", preview.pokemon().get(0).speciesId());
    }

    @Test
    public void formlessNameMapsToTheDefaultForm()
    {   // Genie writes Tornadus with Form "Normal"; PvPoke only has tornadus_incarnate / _therian
        ImportPreview preview = mapOne(row("Tornadus", "Normal", "Bite", "Hurricane", "", false));

        assertEquals("tornadus_incarnate", preview.pokemon().get(0).speciesId());
    }

    @Test
    public void moveNamesMatchIgnoringPunctuation()
    {   // Genie: "Mud-Slap"; PvPoke: "Mud Slap"
        ImportPreview preview = mapOne(row("Diglett", "", "Mud-Slap", "Dig", "", false));

        assertEquals("MUD_SLAP", preview.pokemon().get(0).fastMove());
    }

    @Test
    public void typedMoveVariantIsPickedFromTheLearnset()
    {   // Genie just says "Weather Ball"; Sunny Castform can only know the Fire one
        ImportPreview preview = mapOne(row("Castform", "Sunny", "Ember", "Weather Ball", "", false));

        assertEquals(List.of("WEATHER_BALL_FIRE"), preview.pokemon().get(0).chargedMoves());
    }

    @Test
    public void missingMovesMarkTheRowAndSendItsLearnset()
    {   // Genie only records moves when the scan caught them
        ImportPreview preview = mapOne(row("Swampert", "", "", "", "", false));

        ImportedPokemon swampert = preview.pokemon().get(0);
        assertEquals(null, swampert.fastMove());
        assertEquals(List.of(), swampert.chargedMoves());
        assertEquals(true, swampert.needsMoves());
        assertEquals(Set.of("swampert"), preview.learnsets().keySet());
    }

    @Test
    public void unknownSpeciesIsRejected()
    {   // Ditto isn't released for PvP in the game master
        ImportPreview preview = mapOne(row("Ditto", "", "Transform", "Struggle", "", false));

        assertEquals(List.of(), preview.pokemon());
        assertEquals(List.of(new RejectedRow(1, "Unknown species: Ditto")), preview.rejected());
    }

    @Test
    public void shadowOfASpeciesThatCantBeShadowIsRejected()
    {   // the save step would refuse it, so the preview mustn't call it ready
        ImportPreview preview = mapOne(row("Medicham", "", "Counter", "Ice Punch", "", true));

        assertEquals(List.of(), preview.pokemon());
        assertEquals(List.of(new RejectedRow(1, "Medicham can't be a Shadow")), preview.rejected());
    }

    @Test
    public void parserRejectionsArePassedThrough()
    {
        ImportPreview preview = mapper.map(new PokeGenieImport(List.of(), List.of(new RejectedRow(7, "IVs unknown"))));

        assertEquals(List.of(new RejectedRow(7, "IVs unknown")), preview.rejected());
    }
}

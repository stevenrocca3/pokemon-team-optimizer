package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PvPoke's recommended moveset per species (resources/meta/movesets.json), taken from the League the
 * species ranks best in. Pickers and imports start on these instead of the learnset's first moves.
 */
@Component
public class SuggestedMovesets
{
    private static final String RESOURCE = "/meta/movesets.json";

    private final Map<String, SuggestedMoves> bySpecies = new HashMap<>();

    public SuggestedMovesets(SpeciesLookup speciesLookup)
    {
        try (InputStream is = SuggestedMovesets.class.getResourceAsStream(RESOURCE))
        {
            if (is == null)
            {
                throw new IllegalStateException(RESOURCE + " could not be found");
            }
            JsonNode all = new JsonMapper().readTree(is);
            for (Map.Entry<String, JsonNode> entry : all.properties())
            {
                List<String> moves = new ArrayList<>();
                entry.getValue().forEach(move -> moves.add(move.asString()));
                SuggestedMoves suggested = new SuggestedMoves(moves.get(0), List.copyOf(moves.subList(1, moves.size())));
                // a moveset our gamemaster wouldn't accept (renamed or removed move) is no suggestion at all
                speciesLookup.findById(entry.getKey())
                    .filter(species -> species.learnset().allows(suggested.fastMove(), suggested.chargedMoves()))
                    .ifPresent(species -> bySpecies.put(species.speciesId(), suggested));
            }
        }
        catch (IOException | JacksonException e)
        {
            throw new IllegalStateException(RESOURCE + " couldn't be loaded", e);
        }
    }

    public Optional<SuggestedMoves> forSpecies(String speciesId)
    {
        return Optional.ofNullable(bySpecies.get(speciesId));
    }
}

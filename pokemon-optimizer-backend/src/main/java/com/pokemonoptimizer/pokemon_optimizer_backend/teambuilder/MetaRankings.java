package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PvPoke's top-ranked Pokémon per League (resources/meta/top-*.json: the first 150 of its overall Great and Ultra
 * rankings, 50 of Master's), for the Collection checklist's quick-select grid (#116). A ranked species missing from
 * the species data fails startup, like the meta does.
 */
@Component
public class MetaRankings
{
    private final Map<League, List<MetaPick>> topByLeague = new EnumMap<>(League.class);

    public MetaRankings(SpeciesLookup speciesLookup)
    {
        JsonMapper mapper = new JsonMapper();
        for (League league : League.values())
        {
            List<MetaPick> top = new ArrayList<>();
            for (JsonNode id : read(mapper, "/meta/top-" + league.key + ".json"))
            {
                OwnedSpecies owned = OwnedSpecies.ofPvpokeId(id.asString());
                SpeciesType species = speciesLookup.findById(owned.speciesId())
                    .orElseThrow(() -> new IllegalStateException("Ranked Pokémon " + id.asString() + " isn't in the species data"));
                // a single-typed species has "none" as its second type
                top.add(new MetaPick(owned.speciesId(), owned.shadow(), species.types().stream().filter(t -> !"none".equals(t)).toList()));
            }
            topByLeague.put(league, List.copyOf(top));
        }
    }

    /** Best first. */
    public List<MetaPick> top(League league)
    {
        return topByLeague.get(league);
    }

    private static JsonNode read(JsonMapper mapper, String resource)
    {
        try (InputStream is = MetaRankings.class.getResourceAsStream(resource))
        {
            if (is == null)
            {
                throw new IllegalStateException(resource + " could not be found");
            }
            return mapper.readTree(is);
        }
        catch (IOException | JacksonException e)
        {
            throw new IllegalStateException(resource + " couldn't be loaded", e);
        }
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Combatant;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Moveset;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** PvPoke's meta groups (resources/meta), the opponents the Team Builder battles against (ADR 0005). */
@Component
public class MetaLookup
{
    private static final String SHADOW_SUFFIX = "_shadow";

    private final Map<League, List<MetaPokemon>> metaByLeague = new EnumMap<>(League.class);
    private final String snapshotDate;

    public MetaLookup(SpeciesLookup speciesLookup, MoveLookup moveLookup)
    {
        JsonMapper mapper = new JsonMapper();
        JsonNode gamemaster = read(mapper, "/gamemaster.json");
        snapshotDate = gamemaster.get("timestamp").asString().substring(0, "yyyy-mm-dd".length());
        Map<String, JsonNode> defaultIvs = defaultIvs(gamemaster);
        for (League league : League.values())
        {
            List<MetaPokemon> meta = new ArrayList<>();
            for (JsonNode entry : read(mapper, "/meta/" + league.key + ".json"))
            {
                meta.add(build(entry, league, defaultIvs, speciesLookup, moveLookup));
            }
            metaByLeague.put(league, List.copyOf(meta));
        }
    }

    public List<MetaPokemon> meta(League league)
    {
        return metaByLeague.get(league);
    }

    /** The date of the PvPoke snapshot the meta (and the gamemaster) came from. */
    public String snapshotDate()
    {
        return snapshotDate;
    }

    private static MetaPokemon build(JsonNode entry, League league, Map<String, JsonNode> defaultIvs,
                                     SpeciesLookup speciesLookup, MoveLookup moveLookup)
    {
        String id = entry.get("speciesId").asString();
        boolean shadow = id.endsWith(SHADOW_SUFFIX);
        String baseId = shadow ? id.substring(0, id.length() - SHADOW_SUFFIX.length()) : id;
        SpeciesType species = speciesLookup.findById(baseId)
            .orElseThrow(() -> new IllegalStateException("Meta Pokémon " + id + " isn't in the species data"));

        Combatant combatant;
        if (league.cpCap == 0)
        {
            combatant = new Combatant(species, 15, 15, 15, League.MAX_LEVEL, shadow);
        }
        else
        {
            // [level, atk, def, sta]; a Shadow entry has its own row in the gamemaster, the base species is the fallback
            JsonNode ivs = defaultIvs.getOrDefault(id, defaultIvs.get(baseId)).get("cp" + league.cpCap);
            combatant = new Combatant(species, ivs.get(1).asInt(), ivs.get(2).asInt(), ivs.get(3).asInt(), ivs.get(0).asDouble(), shadow);
        }

        List<Move> charged = new ArrayList<>();
        for (JsonNode move : entry.get("chargedMoves"))
        {
            charged.add(move(moveLookup, move.asString()));
        }
        return new MetaPokemon(id, combatant, new Moveset(move(moveLookup, entry.get("fastMove").asString()), List.copyOf(charged)));
    }

    private static Move move(MoveLookup moveLookup, String id)
    {
        return moveLookup.findById(id).orElseThrow(() -> new IllegalStateException("Meta move " + id + " isn't in the move data"));
    }

    private static Map<String, JsonNode> defaultIvs(JsonNode gamemaster)
    {
        Map<String, JsonNode> bySpecies = new HashMap<>();
        for (JsonNode pokemon : gamemaster.get("pokemon"))
        {
            if (pokemon.has("defaultIVs"))
            {
                bySpecies.put(pokemon.get("speciesId").asString(), pokemon.get("defaultIVs"));
            }
        }
        return bySpecies;
    }

    private static JsonNode read(JsonMapper mapper, String resource)
    {
        try (InputStream is = MetaLookup.class.getResourceAsStream(resource))
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

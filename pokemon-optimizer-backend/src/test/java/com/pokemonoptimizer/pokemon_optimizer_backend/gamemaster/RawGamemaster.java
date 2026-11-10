package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * gamemaster.json read straight off disk, so a test can check the loaders against whatever snapshot is committed.
 * The nightly Gamemaster sync swaps the file; tests that pinned "352 moves" or "2026-10-04" would fail every time.
 */
public final class RawGamemaster
{
    private static final JsonNode GAMEMASTER = read();

    private RawGamemaster() {}

    /** e.g. "2026-10-04 00:17:21" */
    public static String timestamp()
    {
        return GAMEMASTER.get("timestamp").asString();
    }

    /** The rule SpeciesLookup keeps to: released, and not a Shadow entry (Shadow belongs to a Caught Pokémon). */
    public static int releasedSpeciesCount()
    {
        Set<String> ids = new HashSet<>();
        for (JsonNode pokemon : GAMEMASTER.get("pokemon"))
        {
            boolean shadow = false;
            for (JsonNode tag : pokemon.path("tags"))
            {
                shadow |= tag.asString().equals("shadow");
            }
            if (!shadow && pokemon.path("released").asBoolean())
            {
                ids.add(pokemon.get("speciesId").asString());
            }
        }
        return ids.size();
    }

    public static int moveCount()
    {
        return GAMEMASTER.get("moves").size();
    }

    private static JsonNode read()
    {
        try (InputStream in = RawGamemaster.class.getResourceAsStream("/gamemaster.json"))
        {
            return new JsonMapper().readTree(in);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }
}

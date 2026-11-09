package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.io.InputStream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** A stand-in PvPoke: the bundled gamemaster with one Fantasy Cup format, and the bundled Great League group as its meta. */
final class CupFixtures
{
    static final JsonMapper JSON = new JsonMapper();
    static final JsonNode GREAT_GROUP = read("/meta/great.json");

    private CupFixtures() {}

    static JsonNode read(String resource)
    {
        try (InputStream is = CupFixtures.class.getResourceAsStream(resource))
        {
            return JSON.readTree(is);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    static ObjectNode fantasyGamemaster()
    {
        ObjectNode gamemaster = (ObjectNode) read("/gamemaster.json");
        gamemaster.set("formats", JSON.readTree("""
            [{"title": "Fantasy Cup", "cup": "fantasy", "cp": 1500, "meta": "fantasy", "showFormat": true,
              "rules": ["Only Steel, Dragon and Fairy types."]}]"""));
        gamemaster.set("cups", JSON.readTree("""
            [{"name": "fantasy", "title": "Fantasy Cup",
              "include": [{"filterType": "type", "values": ["steel", "dragon", "fairy"]}],
              "exclude": [{"filterType": "tag", "values": ["mega"]}]}]"""));
        return gamemaster;
    }

    /** Serves the Fantasy Cup, or fails every call once {@code broken} is set. */
    static final class FakePvpoke implements PvpokeSource
    {
        boolean broken;

        @Override
        public JsonNode gamemaster()
        {
            if (broken) throw new CupDataException("raw.githubusercontent.com timed out");
            return fantasyGamemaster();
        }

        @Override
        public JsonNode metaGroup(String name)
        {
            if (broken) throw new CupDataException("raw.githubusercontent.com timed out");
            return GREAT_GROUP;
        }
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import tools.jackson.databind.JsonNode;

/** Where PvPoke's open data comes from: its built gamemaster and its meta groups. Both throw {@link CupDataException}. */
public interface PvpokeSource
{
    JsonNode gamemaster();

    /** PvPoke's src/data/groups/{name}.json. */
    JsonNode metaGroup(String name);
}

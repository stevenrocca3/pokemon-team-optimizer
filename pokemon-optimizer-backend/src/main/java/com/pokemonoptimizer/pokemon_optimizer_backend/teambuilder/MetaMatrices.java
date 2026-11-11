package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@link MetaMatrix} for each meta, worked out the first time it's needed and kept. Keyed by League, or by Cup and
 * its PvPoke date, so a Cup's refreshed meta gets a fresh matrix.
 */
final class MetaMatrices
{
    private final Map<String, MetaMatrix> byKey = new ConcurrentHashMap<>();

    MetaMatrix get(String key, List<MetaPokemon> meta, int battleCp)
    {
        return byKey.computeIfAbsent(key, k -> MetaMatrix.of(meta, battleCp));
    }
}

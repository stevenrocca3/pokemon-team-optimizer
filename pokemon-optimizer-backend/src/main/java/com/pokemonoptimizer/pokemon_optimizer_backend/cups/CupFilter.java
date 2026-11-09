package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.util.List;
import java.util.Set;

/**
 * One of a Cup's include or exclude filters, as PvPoke writes them. {@code values} are strings even for the numeric
 * filters ("evolution", "dex"). {@code leagues} limits the filter to those CP caps (PvPoke's 1500, 2500, 10000);
 * empty means every League. {@code includeShadows} makes an include "id" filter cover the Shadow too.
 */
public record CupFilter(String filterType, List<String> values, List<Integer> leagues, boolean includeShadows)
{
    /** The filter types {@link CupRules} understands; a Cup using any other is skipped rather than half-applied. */
    public static final Set<String> SUPPORTED = Set.of("type", "dex", "tag", "evolution", "id", "move");

    public CupFilter
    {
        values = List.copyOf(values);
        leagues = leagues == null ? List.of() : List.copyOf(leagues);
    }
}

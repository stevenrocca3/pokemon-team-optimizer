package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.util.List;

/**
 * A Cup's entry rules: PvPoke's include and exclude filters, applied the way GameMaster.generateFilteredPokemonList
 * does. Every include filter must match, except that a matching "id" filter lets a Pokémon in by itself (and an
 * unmatched one isn't required when there are other filters). Any matching exclude filter keeps it out, unless an
 * include "id" filter named it.
 */
public record CupRules(List<CupFilter> include, List<CupFilter> exclude)
{
    private static final String SHADOW_SUFFIX = "_shadow";

    public CupRules
    {
        include = List.copyOf(include);
        exclude = List.copyOf(exclude);
    }

    /** Whether this entrant may enter at this CP cap (PvPoke's: 1500, 2500 or 10000). */
    public boolean allows(CupEntrant entrant, int cpCap)
    {
        Matches included = matches(include, true, entrant, cpCap);
        if (included.count() < included.required())
        {
            return false;
        }
        return included.namedById() || matches(exclude, false, entrant, cpCap).count() == 0;
    }

    private record Matches(int count, int required, boolean namedById) {}

    private static Matches matches(List<CupFilter> filters, boolean including, CupEntrant entrant, int cpCap)
    {
        int count = 0;
        int required = filters.size();
        boolean namedById = false;
        for (CupFilter filter : filters)
        {
            if (!filter.leagues().isEmpty() && !filter.leagues().contains(cpCap))
            {
                required--;
                continue;
            }
            switch (filter.filterType())
            {
                case "type" -> {
                    if (entrant.types().stream().anyMatch(filter.values()::contains)) count++;
                }
                case "dex" -> {
                    if (inDexRanges(filter.values(), entrant.dex())) count++;
                }
                case "tag" -> count += (int) filter.values().stream().filter(entrant.tags()::contains).count();
                case "evolution" -> {
                    if (filter.values().contains(String.valueOf(entrant.evolutionStage()))) count++;
                }
                case "move" -> count += (int) filter.values().stream().filter(m -> entrant.moves().contains(m.toUpperCase())).count();
                case "id" -> {
                    if (including && filters.size() > 1)
                    {
                        required--;   // an id filter is an alternative way in, not one more requirement
                    }
                    String testId = !including || filter.includeShadows() ? entrant.id().replace(SHADOW_SUFFIX, "") : entrant.id();
                    if (filter.values().contains(testId) || filter.values().contains(entrant.id()))
                    {
                        count += filters.size();   // named outright: every other filter is beside the point
                        namedById |= including;
                    }
                }
                default -> throw new IllegalStateException("Unsupported Cup filter " + filter.filterType());
            }
        }
        return new Matches(count, required, namedById);
    }

    /** PvPoke's "dex" values: [from, to, from, to, …], inclusive; a trailing odd value is ignored. */
    private static boolean inDexRanges(List<String> values, int dex)
    {
        for (int k = 0; k + 1 < values.size(); k += 2)
        {
            if (dex >= Integer.parseInt(values.get(k)) && dex <= Integer.parseInt(values.get(k + 1)))
            {
                return true;
            }
        }
        return false;
    }
}

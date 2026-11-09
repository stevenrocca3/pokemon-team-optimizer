package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

import tools.jackson.databind.JsonNode;

/**
 * Reads PvPoke's gamemaster "formats" (what pvpoke.com offers right now) and "cups" (each one's entry filters), plus
 * each format's meta group, into {@link CupFormat}s. A format DexPresso can't model faithfully is skipped with a
 * note rather than half-applied; a meta Pokémon it can't build (a species or move newer than DexPresso's data) is
 * dropped with a note. Data that's missing or broken throws, so the last good Cups stay in use.
 */
public final class PvpokeCups
{
    /** Below this, random meta Teams of three different species are too few to mean anything. */
    static final int MIN_META = 8;
    private static final int MIN_META_SPECIES = 3;
    private static final String SHADOW_SUFFIX = "_shadow";
    private static final int NO_CAP = 10000;
    private static final Set<Integer> SUPPORTED_CAPS = Set.of(1500, 2500, NO_CAP);
    /** Cup settings that restrict picks in ways the rules below can't express. */
    private static final Set<String> RESTRICTING = Set.of("slots", "restrictedPokemon", "restrictedPicks", "tierRules", "pointLimit");

    private PvpokeCups() {}

    public static ParsedCups parse(JsonNode gamemaster, Function<String, JsonNode> metaGroup, SpeciesLookup species, MoveLookup moves)
    {
        JsonNode formats = gamemaster.get("formats");
        JsonNode cups = gamemaster.get("cups");
        JsonNode pokemon = gamemaster.get("pokemon");
        JsonNode timestamp = gamemaster.get("timestamp");
        if (formats == null || !formats.isArray() || cups == null || !cups.isArray() || pokemon == null || !pokemon.isArray()
            || timestamp == null)
        {
            throw new CupDataException("PvPoke's gamemaster is missing its formats, cups, pokemon or timestamp");
        }
        Map<String, JsonNode> cupsByName = new HashMap<>();
        cups.forEach(cup -> cupsByName.put(text(cup, "name"), cup));
        Map<String, JsonNode> defaultIvs = new HashMap<>();
        pokemon.forEach(p -> {
            if (p.has("defaultIVs")) defaultIvs.put(text(p, "speciesId"), p.get("defaultIVs"));
        });

        List<CupFormat> parsed = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (JsonNode format : formats)
        {
            String cupName = text(format, "cup");
            if (!format.path("showFormat").asBoolean(false) || "custom".equals(cupName))
            {
                continue;
            }
            int cp = format.path("cp").asInt(0);
            String title = text(format, "title");
            String label = title + " (" + cp + ")";
            JsonNode cup = cupsByName.get(cupName);
            String problem = problem(cup, cp);
            if (problem != null)
            {
                notes.add(label + ": skipped, " + problem);
                continue;
            }
            String key = cupName + "-" + cp;
            if (!keys.add(key))
            {
                continue;   // PvPoke lists a few formats twice
            }
            List<MetaEntry> meta = meta(metaGroup.apply(text(format, "meta")), cp, defaultIvs, species, moves, label, notes);
            if (meta.size() < MIN_META || meta.stream().map(m -> species.findById(m.speciesId()).orElseThrow().dex()).distinct().count() < MIN_META_SPECIES)
            {
                notes.add(label + ": skipped, its meta has only " + meta.size() + " Pokémon DexPresso can build");
                continue;
            }
            List<String> rules = new ArrayList<>();
            format.path("rules").forEach(rule -> rules.add(tidy(rule.asString())));
            parsed.add(new CupFormat(key, cupName, title, cp, cup.path("partySize").asInt(3) == 6 ? 6 : 3, List.copyOf(rules),
                new CupRules(filters(cup.get("include")), filters(cup.get("exclude"))), List.copyOf(meta)));
        }
        if (parsed.isEmpty())
        {
            throw new CupDataException("No usable formats in PvPoke's data" + (notes.isEmpty() ? "" : ": " + String.join("; ", notes)));
        }
        return new ParsedCups(timestamp.asString(), List.copyOf(parsed), List.copyOf(notes));
    }

    /** Why this Cup can't be modelled, or null. */
    private static String problem(JsonNode cup, int cp)
    {
        if (!SUPPORTED_CAPS.contains(cp))
        {
            return cp + " CP Cups aren't supported yet";
        }
        if (cup == null)
        {
            return "PvPoke has no rules for it";
        }
        if (cup.has("levelCap") && cup.get("levelCap").asDouble() != 50)
        {
            return "its level cap of " + cup.get("levelCap").asString() + " isn't supported yet";
        }
        for (String setting : RESTRICTING)
        {
            if (cup.has(setting))
            {
                return "its '" + setting + "' pick restrictions aren't supported yet";
            }
        }
        for (String side : List.of("include", "exclude"))
        {
            JsonNode filters = cup.get(side);
            if (filters == null || !filters.isArray())
            {
                return "its " + side + " filters are missing";
            }
            for (JsonNode filter : filters)
            {
                String type = text(filter, "filterType");
                if (!CupFilter.SUPPORTED.contains(type) || !filter.path("values").isArray())
                {
                    return "uses filter '" + type + "' that DexPresso doesn't apply yet";
                }
            }
        }
        return null;
    }

    private static List<CupFilter> filters(JsonNode filters)
    {
        List<CupFilter> parsed = new ArrayList<>();
        for (JsonNode filter : filters)
        {
            List<String> values = new ArrayList<>();
            filter.get("values").forEach(v -> values.add(v.isNumber() ? String.valueOf(v.asLong()) : v.asString()));
            List<Integer> leagues = new ArrayList<>();
            filter.path("leagues").forEach(l -> leagues.add(l.asInt()));
            parsed.add(new CupFilter(text(filter, "filterType"), values, leagues, filter.path("includeShadows").asBoolean(false)));
        }
        return parsed;
    }

    private static List<MetaEntry> meta(JsonNode group, int cp, Map<String, JsonNode> defaultIvs, SpeciesLookup species,
                                        MoveLookup moves, String label, List<String> notes)
    {
        if (group == null || !group.isArray())
        {
            throw new CupDataException(label + ": its meta group isn't a list");
        }
        List<MetaEntry> meta = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode entry : group)
        {
            String id = text(entry, "speciesId");
            boolean shadow = id.endsWith(SHADOW_SUFFIX);
            String baseId = shadow ? id.substring(0, id.length() - SHADOW_SUFFIX.length()) : id;
            String why = null;
            Optional<SpeciesType> found = species.findById(baseId);
            List<String> charged = new ArrayList<>();
            entry.path("chargedMoves").forEach(m -> charged.add(m.asString()));
            if (charged.size() > 2 && found.isPresent())
            {
                // some Megas get a third, Mega-only charged move; battles here use two, like the rest of the meta
                notes.add(label + ": " + id + " battles without " + String.join(", ", charged.subList(2, charged.size())));
                charged.subList(2, charged.size()).clear();
            }
            String fast = text(entry, "fastMove");
            JsonNode ivs = cp == NO_CAP ? null : defaultIvs.getOrDefault(id, defaultIvs.get(baseId));
            ivs = ivs == null ? null : ivs.get("cp" + cp);
            if (found.isEmpty())
            {
                why = "isn't in DexPresso's species data yet";
            }
            else if (fast.isEmpty() || charged.isEmpty())
            {
                why = "has no moveset in the meta group";
            }
            else if (!found.get().learnset().allows(fast, charged) || unsimulated(fast, charged, moves) != null)
            {
                String move = unsimulated(fast, charged, moves);
                why = move == null ? "has moves its learnset here doesn't allow" : "uses " + move + ", which isn't simulated yet";
            }
            else if (cp != NO_CAP && (ivs == null || ivs.size() < 4))
            {
                why = "has no default IVs for " + cp + " CP";
            }
            if (why != null)
            {
                notes.add(label + ": dropped " + id + " from the meta, it " + why);
                continue;
            }
            if (!seen.add(id))
            {
                continue;
            }
            meta.add(cp == NO_CAP
                ? new MetaEntry(baseId, shadow, fast, List.copyOf(charged), 50, 15, 15, 15)
                : new MetaEntry(baseId, shadow, fast, List.copyOf(charged), ivs.get(0).asDouble(), ivs.get(1).asInt(), ivs.get(2).asInt(), ivs.get(3).asInt()));
        }
        return meta;
    }

    /** The first move DexPresso doesn't have or can't simulate (two-sided buffs), or null. */
    private static String unsimulated(String fast, List<String> charged, MoveLookup moves)
    {
        List<String> all = new ArrayList<>(charged);
        all.add(0, fast);
        for (String id : all)
        {
            Optional<Move> move = moves.findById(id);
            if (move.isEmpty())
            {
                return id;
            }
            if ("both".equals(move.get().buffTarget()))
            {
                return move.get().name();
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field)
    {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }

    /** PvPoke's rule lines come with ",  " and "1, 500 CP"; tidy them for reading. */
    static String tidy(String rule)
    {
        return rule.replaceAll("(\\d), (\\d{3})", "$1,$2").replaceAll(",\\s+", ", ").trim();
    }
}

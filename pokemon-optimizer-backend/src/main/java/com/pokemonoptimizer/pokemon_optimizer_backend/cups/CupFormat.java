package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.util.List;

/**
 * One live PvPoke format: a Cup at a CP cap (1500, 2500, or 10000 for no cap), its rules for people to read, its
 * entry filters, and the meta its Teams are battled against. {@code key} ("fantasy-1500") is how the API names it.
 * {@code partySize} is 6 for Play! Pokémon's pick-3-of-6 formats, otherwise 3.
 */
public record CupFormat(String key, String cup, String title, int cpCap, int partySize, List<String> rules,
                        CupRules eligibility, List<MetaEntry> meta) {}

package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

/** One of a League's top-ranked Pokémon for the Collection's quick-select grid (#116), with its types for the badges. */
public record MetaPick(String speciesId, boolean shadow, List<String> types) {}

package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;

import java.util.List;

/**
 * What Cup rules look at besides types and the Learnset: PvPoke's tags ("legendary", "mega", "shadoweligible", …)
 * and the evolution stage PvPoke's "evolution" filter matches: 0 doesn't evolve and has no pre-evolution, 1 evolves
 * and has none, 2 evolves and has one, 3 is fully evolved from something.
 */
public record SpeciesTraits(List<String> tags, int evolutionStage) {}

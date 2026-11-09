package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

/** Best first: most meta wins, then the higher average rating. {@code flips} are where they differ. */
public record IvComparisonResult(League league, int metaSize, String metaDate, List<ComparedPokemon> pokemon,
                                 List<FlippedMatchup> flips) {}

package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

public record LeagueProjection(League league, boolean eligible, Double level, Integer cp, Double statProduct,
                               Integer statProductRank, Double percentOfBest, boolean needsXlCandy, Integer bestBuddyCp) {}

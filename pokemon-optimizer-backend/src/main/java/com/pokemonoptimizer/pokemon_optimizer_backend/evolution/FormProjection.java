package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import java.util.List;

public record FormProjection(String speciesId, String name, int dex, int stage, List<String> path, List<LeagueProjection> leagues) {}

package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import java.util.List;

public record EvolutionProjection(String speciesId, String name, boolean shadow, List<FormProjection> forms) {}

package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import java.util.List;

/** A later stage a species can evolve into: how many evolutions away ({@code stage}), and the line from the species to it. */
public record EvolvedForm(String speciesId, int stage, List<String> path) {}

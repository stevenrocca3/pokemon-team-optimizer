package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

/** An exclusive move in a form's best moveset, and how to get it. */
public record MoveWarning(String moveId, String name, boolean fast, String message) {}

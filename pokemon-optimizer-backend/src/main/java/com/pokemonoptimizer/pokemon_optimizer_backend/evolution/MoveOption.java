package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

/**
 * A move a form can learn. {@code exclusive}: it isn't in the regular pool, so it takes an Elite TM or an event
 * (Community Day moves, event moves, legacy moves). {@code legacy}: it was in the regular pool once and was removed.
 */
public record MoveOption(String moveId, String name, boolean exclusive, boolean legacy) {}

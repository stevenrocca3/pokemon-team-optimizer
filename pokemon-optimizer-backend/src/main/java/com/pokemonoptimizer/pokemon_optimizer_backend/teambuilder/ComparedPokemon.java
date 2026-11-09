package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

/**
 * One of the Pokémon being compared, built for the League. {@code wins} counts meta battles won across the 0, 1
 * and 2 shield scenarios (out of {@code battles}). {@code problem} is why it can't play there; then the numbers are 0.
 */
public record ComparedPokemon(int index, double level, int cp, int wins, int battles, double metaRating, String problem) {}

package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

/** A Pokémon from the request the Team Builder couldn't use, and why. {@code index} is its position in the request. */
public record SkippedPokemon(int index, String reason) {}

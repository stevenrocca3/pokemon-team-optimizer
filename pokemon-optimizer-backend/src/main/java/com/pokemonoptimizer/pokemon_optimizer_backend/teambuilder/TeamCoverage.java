package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

/**
 * How a Team covers the meta (#113). {@code rating}: for each meta Pokémon the best 1v1 battle rating any member gets
 * against it, averaged (500 = even): the synergy score the Team Builder shortlists on. {@code answered}: how many meta
 * Pokémon at least one member beats (rating over 500), of {@code metaSize}.
 */
public record TeamCoverage(double rating, int answered, int metaSize) {}

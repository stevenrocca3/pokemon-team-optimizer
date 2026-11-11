package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

/**
 * A meta Pokémon a Team struggles with (#113): {@code opponent} is its PvPoke ID, {@code rating} the best 1v1 battle
 * rating any member gets against it (under 500 is a loss), and {@code answer} the member's species that rates it best.
 */
public record Threat(String opponent, double rating, String answer) {}

package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

/** One meta battle: PvPoke's id for the opponent, and the shields each side starts with. */
public record MatchupRef(String opponent, int shields) {}

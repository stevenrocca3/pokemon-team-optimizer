package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

/** A CSV row that couldn't be imported. {@code index} is Genie's Index column, so players can find it. */
public record RejectedRow(int index, String reason) {}

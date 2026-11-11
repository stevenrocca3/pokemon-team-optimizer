package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

/**
 * One evolution and what it takes in Pokémon GO (#139): Candy, an item ({@code itemCount} of it: 1 Sinnoh Stone, 999
 * Gimmighoul Coins), a Lure Module, and any other conditions in words ("Walk 10 km as your buddy"). {@code item} and
 * {@code lure} are null when it needs none.
 */
public record EvolutionStep(String from, String into, int candy, String item, int itemCount, String lure, List<String> conditions) {}

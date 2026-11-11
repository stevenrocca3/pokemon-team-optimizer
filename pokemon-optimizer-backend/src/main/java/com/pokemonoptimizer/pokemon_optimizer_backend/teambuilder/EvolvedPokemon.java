package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;

/**
 * One of the request's Pokémon as a form it can evolve into, for the Team Builder to try in its place (#138).
 * {@code index} is the original's position in the request, so a Team never holds a Pokémon and its own evolution.
 * {@code pokemon} keeps the original's IVs, level and Shadow status, with the evolved form's best moveset.
 */
public record EvolvedPokemon(int index, CaughtPokemonRequest pokemon, Evolution evolution) {}

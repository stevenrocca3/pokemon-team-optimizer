package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A gamemaster entry's evolution family; {@code parent} and {@code evolutions} are missing when there are none. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Family(String id, String parent, List<String> evolutions) {}

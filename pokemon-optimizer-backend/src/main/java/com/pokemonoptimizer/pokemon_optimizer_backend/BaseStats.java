package com.pokemonoptimizer.pokemon_optimizer_backend;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BaseStats(int atk, int def, int hp) {}

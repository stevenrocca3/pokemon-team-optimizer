package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

public record RankRequest(String speciesId,
                           boolean hasCap, int cap, boolean hasBestBuddy) {}
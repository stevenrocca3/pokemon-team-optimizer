package com.pokemonoptimizer.pokemon_optimizer_backend;

public record RankRequest(int baseAtk, int baseDef, int baseSta,
                           boolean hasCap, int cap, boolean hasBestBuddy) {}
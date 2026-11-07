package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;

public record Move(String moveId, String name, String type, int power, int energy, int energyGain, int turns) {}


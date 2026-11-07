package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;

import java.util.List;

public record Move(String moveId, String name, String type, int power, int energy, int energyGain, int turns, 
    List<Integer> buffs, String buffTarget, double buffApplyChance) {}


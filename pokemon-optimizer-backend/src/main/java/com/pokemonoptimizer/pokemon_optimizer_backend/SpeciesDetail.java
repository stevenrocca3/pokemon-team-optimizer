package com.pokemonoptimizer.pokemon_optimizer_backend;

public record SpeciesDetail(String id, String name, int dex, 
                            int baseAtk, int baseDef, int baseSta, Learnset learnset){};
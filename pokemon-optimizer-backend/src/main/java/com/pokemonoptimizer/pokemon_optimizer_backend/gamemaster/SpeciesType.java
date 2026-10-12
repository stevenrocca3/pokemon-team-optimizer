package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;

public record SpeciesType(int dex, String speciesName, 
                            String speciesId, int baseSta, int baseAtk, int baseDef, Learnset learnset){}

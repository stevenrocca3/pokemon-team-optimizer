package com.pokemonoptimizer.pokemon_optimizer_backend;

// If in base form, i.e. not galarian or anything, form = ""
public record SpeciesType(int dexNumber, String name, String form, 
                            int baseSta, int baseAtk, int baseDef){}

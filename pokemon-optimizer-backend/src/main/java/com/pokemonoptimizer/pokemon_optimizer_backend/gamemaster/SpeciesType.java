package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;
import java.util.List;

public record SpeciesType(int dex, String speciesName, String speciesId, 
            int baseSta, int baseAtk, int baseDef, Learnset learnset, 
            List<String> types, boolean shadowEligible){}

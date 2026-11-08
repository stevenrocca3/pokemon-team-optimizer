package com.pokemonoptimizer.pokemon_optimizer_backend.dto;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Learnset;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMoves;

public record SpeciesDetail(String id, String name, int dex, 
                            int baseAtk, int baseDef, int baseSta, Learnset learnset, boolean shadowEligible,
                            SuggestedMoves suggestedMoves /* null when PvPoke doesn't rank it */){};
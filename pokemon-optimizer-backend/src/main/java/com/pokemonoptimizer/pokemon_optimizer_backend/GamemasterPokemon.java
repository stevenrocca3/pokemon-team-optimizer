package com.pokemonoptimizer.pokemon_optimizer_backend;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GamemasterPokemon(BaseStats baseStats, int dex, String speciesName, 
                        String speciesId, List<String> tags, boolean released, List<String> fastMoves, List<String> chargedMoves, List<String> eliteMoves, List<String> legacyMoves) {
                            public GamemasterPokemon 
                            {
                                if (tags == null) tags = List.of();
                                if (eliteMoves == null) eliteMoves = List.of();
                                if (legacyMoves == null) legacyMoves = List.of();

                            }
                        }
        

package com.pokemonoptimizer.pokemon_optimizer_backend;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GamemasterPokemon(BaseStats baseStats, int dex, String speciesName, 
                        String speciesId, List<String> tags, boolean released) {}

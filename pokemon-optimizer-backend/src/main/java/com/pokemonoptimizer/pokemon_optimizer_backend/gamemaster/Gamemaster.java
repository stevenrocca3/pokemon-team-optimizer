package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Gamemaster(List<GamemasterPokemon> pokemon, List<GamemasterMove> moves){}

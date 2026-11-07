package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GamemasterMove(String moveId, String name, String type, int power, int energy, int energyGain, int turns) {}

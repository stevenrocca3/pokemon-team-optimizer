package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record BreakpointRequest(@NotNull League league, @NotNull @Valid CaughtPokemonRequest pokemon) {}

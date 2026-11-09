package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

import java.util.List;

import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A few Pokémon to compare for one League, usually copies of one species. */
public record CompareRequest(@NotNull League league,
                             @NotNull @Size(min = 2, max = 6) List<@NotNull @Valid CaughtPokemonRequest> pokemon) {}

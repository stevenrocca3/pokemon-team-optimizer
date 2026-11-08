package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

import java.util.List;

import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The Pokémon to build Teams from: a Trainer's Collection, or a guest's own list. */
public record TeamBuilderRequest(@NotNull League league,
                                 @NotEmpty @Size(max = 10000) List<@NotNull @Valid CaughtPokemonRequest> pokemon) {}

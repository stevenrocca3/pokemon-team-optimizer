package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Several Pokémon saved at once, e.g. from an import. Saved all together or not at all; ones already in the Collection are skipped. */
public record CaughtPokemonBatch(@NotEmpty @Size(max = 10000) List<@NotNull @Valid CaughtPokemonRequest> pokemon) {}

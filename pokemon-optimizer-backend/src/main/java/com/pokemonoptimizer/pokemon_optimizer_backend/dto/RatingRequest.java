package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A star rating and the page it was left on. Integer, not int, so a missing value fails @NotNull instead of becoming 0. */
public record RatingRequest(@NotNull @Min(1) @Max(5) Integer stars, @Size(max = 200) String page) {}

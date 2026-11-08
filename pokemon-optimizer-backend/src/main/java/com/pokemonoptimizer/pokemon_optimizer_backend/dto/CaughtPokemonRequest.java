package com.pokemonoptimizer.pokemon_optimizer_backend.dto;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CaughtPokemonRequest(
    @NotBlank String speciesId, @NotNull @Min(0) @Max(15) Integer atkIv, 
    @NotNull @Min(0) @Max(15) Integer defIv, 
    @NotNull @Min(0) @Max(15) Integer staIv,
    @NotNull @DecimalMin("1") @DecimalMax("51") Double level, 
    @NotBlank String fastMove, @NotNull @Size(min = 1, max = 2) List<@NotBlank String> chargedMoves) {}

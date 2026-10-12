package com.pokemonoptimizer.pokemon_optimizer_backend.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;

public record CaughtPokemonRequest(@NotBlank String speciesId, @Min(0) @Max(15) int atkIv, @Min(0) @Max(15) int defIv, 
                                    @Min(0) @Max(15) int staIv, @DecimalMin("1") @DecimalMax("51") double level) {
    
}

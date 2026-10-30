package com.pokemonoptimizer.pokemon_optimizer_backend.dto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CaughtPokemonRequest(@NotBlank String speciesId, @NotNull @Min(0) @Max(15) Integer atkIv, @NotNull @Min(0) @Max(15) Integer defIv, 
                                    @NotNull @Min(0) @Max(15) Integer staIv, @NotNull @DecimalMin("1") @DecimalMax("51") Double level) {
    
}

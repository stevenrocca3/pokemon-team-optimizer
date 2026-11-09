package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

import jakarta.validation.constraints.NotBlank;

/** A species ticked on the Collection checklist: "I have one of these", without exact IVs. */
public record OwnedSpecies(@NotBlank String speciesId, Boolean shadow)
{
    public OwnedSpecies
    {
        if (shadow == null) { shadow = false; }
    }
}

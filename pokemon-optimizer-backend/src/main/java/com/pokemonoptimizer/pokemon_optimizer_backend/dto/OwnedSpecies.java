package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

import jakarta.validation.constraints.NotBlank;

/** A species ticked on the Collection checklist: "I have one of these", without exact IVs. */
public record OwnedSpecies(@NotBlank String speciesId, Boolean shadow)
{
    public OwnedSpecies
    {
        if (shadow == null) { shadow = false; }
    }

    /** PvPoke's ids ("annihilape_shadow") split into species and Shadow. */
    public static OwnedSpecies ofPvpokeId(String id)
    {
        return id.endsWith("_shadow") ? new OwnedSpecies(id.substring(0, id.length() - "_shadow".length()), true) : new OwnedSpecies(id, false);
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

/** One Pokémon of a checklist quick-select grid (#116, #117): which species, whether it's the Shadow, and its types. */
public record MetaPick(String speciesId, boolean shadow, List<String> types)
{
    public static MetaPick of(OwnedSpecies owned, SpeciesType species)
    {
        // a single-typed species has "none" as its second type
        return new MetaPick(owned.speciesId(), owned.shadow(), species.types().stream().filter(t -> !"none".equals(t)).toList());
    }
}

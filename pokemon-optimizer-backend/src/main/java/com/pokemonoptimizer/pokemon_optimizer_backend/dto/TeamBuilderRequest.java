package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

import java.util.List;

import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The Pokémon to build Teams from: exact ones (a Trainer's Collection, or a guest's own list) and species ticked
 * on the checklist, which battle with assumed good IVs. Either list may be missing. {@code cup} ("fantasy-1500")
 * builds for a live Cup in that League instead of the open League.
 */
public record TeamBuilderRequest(@NotNull League league,
                                 @Size(max = 10000) List<@NotNull @Valid CaughtPokemonRequest> pokemon,
                                 @Size(max = 2000) List<@NotNull @Valid OwnedSpecies> species,
                                 @Size(max = 128) String cup)
{
    public TeamBuilderRequest
    {
        if (pokemon == null) { pokemon = List.of(); }
        if (species == null) { species = List.of(); }
    }
}

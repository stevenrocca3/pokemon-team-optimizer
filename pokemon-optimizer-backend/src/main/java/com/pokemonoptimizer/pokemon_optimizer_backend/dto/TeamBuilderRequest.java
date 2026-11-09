package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

import java.util.List;

import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The Pokémon to build Teams from: exact ones (a Trainer's Collection, or a guest's own list) and species ticked
 * on the checklist, which battle with assumed good IVs. Either list may be missing.
 */
public record TeamBuilderRequest(@NotNull League league,
                                 @Size(max = 10000) List<@NotNull @Valid CaughtPokemonRequest> pokemon,
                                 @Size(max = 2000) List<@NotNull @Valid OwnedSpecies> species)
{
    public TeamBuilderRequest
    {
        if (pokemon == null) { pokemon = List.of(); }
        if (species == null) { species = List.of(); }
    }
}

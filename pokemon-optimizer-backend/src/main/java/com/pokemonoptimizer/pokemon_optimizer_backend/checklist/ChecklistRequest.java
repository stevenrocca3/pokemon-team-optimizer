package com.pokemonoptimizer.pokemon_optimizer_backend.checklist;

import java.util.List;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The whole checklist, as the page has it now. Capped like the Team Builder's species list. */
public record ChecklistRequest(@NotNull @Size(max = 2000) List<@NotNull @Valid OwnedSpecies> species) {}

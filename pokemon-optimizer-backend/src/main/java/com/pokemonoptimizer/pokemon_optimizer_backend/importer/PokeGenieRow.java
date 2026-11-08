package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

/**
 * One row of a Poké Genie CSV export, as written: display names ("Mud Shot"), not our IDs.
 * Mapping to species and move IDs is a separate step.
 */
public record PokeGenieRow(String name, String form, int atkIv, int defIv, int staIv, double level,
                           String fastMove, String chargedMove1, String chargedMove2, boolean shadow) {}

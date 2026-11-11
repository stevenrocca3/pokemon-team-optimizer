package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

/**
 * A Team member that's one of the Trainer's Pokémon evolved: {@code fromSpeciesId} is what they have now (PvPoke ID).
 * {@code moveWarnings} say when its best moveset needs a Community Day or event move (#137). {@code cost} is the Candy,
 * items and second move it takes (#139).
 */
public record Evolution(String fromSpeciesId, List<String> moveWarnings, BuildCost cost) {}

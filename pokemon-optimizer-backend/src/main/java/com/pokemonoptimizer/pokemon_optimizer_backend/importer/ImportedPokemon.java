package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

import java.util.List;

/**
 * A Poké Genie row mapped to our IDs. {@code fastMove} is null and {@code chargedMoves} may be empty
 * when Genie didn't record the moves (or named one we can't place); {@code needsMoves} says the
 * player has to pick them before this Pokémon can be saved.
 */
public record ImportedPokemon(int index, String speciesId, int atkIv, int defIv, int staIv, double level, boolean shadow,
                              String fastMove, List<String> chargedMoves, boolean needsMoves) {}

package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

/**
 * A Pokémon on a recommended Team, as it would battle: {@code level} and {@code cp} after building it for the
 * League. {@code index} is its position in the request. {@code metaRating} is its average 1v1 battle rating
 * against the meta (500 = even), the screening score. {@code assumed} is a ticked checklist species battling with
 * {@link GoodIvs}, not a Pokémon the Trainer entered.
 */
public record TeamMember(int index, String speciesId, boolean shadow, double level, int cp, int atkIv, int defIv, int staIv,
                         String fastMove, List<String> chargedMoves, double metaRating, boolean assumed) {}

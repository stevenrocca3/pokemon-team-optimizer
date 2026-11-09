package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

/** A meta battle some of the compared Pokémon win and others don't. {@code opponent} is PvPoke's id. */
public record FlippedMatchup(String opponent, int shields, List<Integer> winners) {}

package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.util.List;

/** One Pokémon of a Cup's meta, resolved at sync time to the moves and IVs PvPoke battles it with. */
public record MetaEntry(String speciesId, boolean shadow, String fastMove, List<String> chargedMoves, double level,
                        int atkIv, int defIv, int staIv) {}

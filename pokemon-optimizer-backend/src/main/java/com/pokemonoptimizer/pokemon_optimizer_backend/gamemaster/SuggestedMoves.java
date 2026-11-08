package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;

import java.util.List;

/** A moveset worth defaulting to: PvPoke's recommended one for the League the species ranks best in. */
public record SuggestedMoves(String fastMove, List<String> chargedMoves) {}

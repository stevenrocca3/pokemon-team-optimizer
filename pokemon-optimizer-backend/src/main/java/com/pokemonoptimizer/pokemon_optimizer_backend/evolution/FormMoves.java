package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import java.util.List;

/** A form's movepool, its best moveset (PvPoke's recommendation), and a warning per exclusive move that set needs. */
public record FormMoves(Movepool movepool, String optimalFastMove, List<String> optimalChargedMoves, List<MoveWarning> warnings) {}

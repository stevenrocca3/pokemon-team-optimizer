package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import java.util.List;

/** Everything a form can learn, exclusives included and tagged. */
public record Movepool(List<MoveOption> fastMoves, List<MoveOption> chargedMoves) {}

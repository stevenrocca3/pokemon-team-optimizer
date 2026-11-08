package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import java.util.List;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;

public record Moveset(Move fastMove, List<Move> chargedMoves) {}

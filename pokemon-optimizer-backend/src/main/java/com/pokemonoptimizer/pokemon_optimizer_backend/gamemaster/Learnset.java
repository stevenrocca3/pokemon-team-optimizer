package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;
import java.util.Set;
import java.util.List;

public record Learnset(List<String> fastMoves, List<String> chargedMoves, Set<String> eliteMoves){}

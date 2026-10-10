package com.pokemonoptimizer.pokemon_optimizer_backend;
import java.util.Set;
import java.util.List;

public record Learnset(List<String> fastMoves, List<String> chargedMoves, Set<String> eliteMoves){}

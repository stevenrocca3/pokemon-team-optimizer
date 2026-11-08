package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;
import java.util.Set;
import java.util.List;

public record Learnset(List<String> fastMoves, List<String> chargedMoves, Set<String> eliteMoves){
    /** Whether this species can know this fast move and these charged moves. */
    public boolean allows(String fastMove, List<String> chargedMoves)
    {
        return fastMoves.contains(fastMove) && this.chargedMoves.containsAll(chargedMoves);
    }
}

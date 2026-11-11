package com.pokemonoptimizer.pokemon_optimizer_backend.history;

public class BattleOwnedElsewhereException extends RuntimeException
{
    public BattleOwnedElsewhereException()
    {
        super("This battle is already saved to someone else’s history.");
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.history;

public class AnalysisNotFoundException extends RuntimeException
{
    public AnalysisNotFoundException()
    {
        super("No analysis with that ID.");
    }
}

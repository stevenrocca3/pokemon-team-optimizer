package com.pokemonoptimizer.pokemon_optimizer_backend.history;

public class AnalysisNotFinishedException extends RuntimeException
{
    public AnalysisNotFinishedException()
    {
        super("This analysis isn’t finished yet.");
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

/** The uploaded file is missing columns every Poké Genie export has. */
public class NotAPokeGenieExportException extends RuntimeException
{
    public NotAPokeGenieExportException(String message)
    {
        super(message);
    }
}

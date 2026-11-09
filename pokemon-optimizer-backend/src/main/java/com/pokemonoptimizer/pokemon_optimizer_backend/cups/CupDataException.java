package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

/** PvPoke's data couldn't be fetched or doesn't look like it should; the last good Cups stay in use. */
public class CupDataException extends RuntimeException
{
    public CupDataException(String message)
    {
        super(message);
    }

    public CupDataException(String message, Throwable cause)
    {
        super(message, cause);
    }
}

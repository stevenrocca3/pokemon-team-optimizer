package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import java.util.UUID;

/**
 * Who is saving a battle: a logged-in Trainer (their Google ID), else a guest's session token. A logged-in request
 * may carry the token too: it's the guest the battles were saved under before logging in.
 */
public record BattleOwner(String googleId, UUID guestToken)
{
    public BattleOwner
    {
        if (googleId == null && guestToken == null)
        {
            throw new IllegalArgumentException("A battle needs an owner: a login or a guest token");
        }
    }

    public static BattleOwner trainer(String googleId)
    {
        return new BattleOwner(googleId, null);
    }

    public static BattleOwner guest(UUID guestToken)
    {
        return new BattleOwner(null, guestToken);
    }
}

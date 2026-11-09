package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

/**
 * Fast move damage both ways against one meta Pokémon. {@code attackForMore} is the Attack stat at which your fast
 * move does one more damage (null when no IV spread reaches it under the Cap); {@code defenseForLess} the Defense
 * at which theirs does one less to you (null when none does, or it's already 1).
 */
public record OpponentBreakpoint(String opponent, String opponentFastMove, int damageDealt, Double attackForMore,
                                 int damageTaken, Double defenseForLess) {}

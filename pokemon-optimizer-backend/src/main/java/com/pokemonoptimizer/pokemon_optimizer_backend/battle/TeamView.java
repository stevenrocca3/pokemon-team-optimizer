package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import java.util.List;

/** What a policy can see of one side: its Pokémon, who is in, and its switch timer (PvPoke's Player). */
record TeamView(List<BattlePokemon> team, BattlePokemon active, int switchTimer)
{
    /** Player.getRemainingPokemon */
    int remaining()
    {
        int count = 0;
        for (BattlePokemon poke : team)
        {
            if (poke.hp > 0)
            {
                count++;
            }
        }
        return count;
    }

    /** Player.getShields: the team's shields travel with whoever is in. */
    int shields()
    {
        return active.shields;
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Battle;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.BattleResult;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Combatant;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Moveset;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.ShieldScenario;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Winner;

/**
 * One Pokémon against every meta Pokémon at 0, 1 and 2 shields each: the record behind comparing copies and
 * power-ups. Battle {@code b} is opponent {@code b / 3} at {@code SHIELDS[b % 3]} shields.
 */
final class MetaBattles
{
    static final int[] SHIELDS = { 0, 1, 2 };

    /** won[b] for each battle, and the average battle rating (500 = even). */
    record Record(boolean[] won, double rating)
    {
        int wins()
        {
            int wins = 0;
            for (boolean w : won)
            {
                if (w) wins++;
            }
            return wins;
        }
    }

    private MetaBattles() {}

    static int battles(List<MetaPokemon> meta)
    {
        return meta.size() * SHIELDS.length;
    }

    static MatchupRef matchup(List<MetaPokemon> meta, int battle)
    {
        return new MatchupRef(meta.get(battle / SHIELDS.length).id(), SHIELDS[battle % SHIELDS.length]);
    }

    static Record against(Combatant combatant, Moveset moveset, List<MetaPokemon> meta)
    {
        boolean[] won = new boolean[battles(meta)];
        double total = 0;
        for (int m = 0; m < meta.size(); m++)
        {
            for (int s = 0; s < SHIELDS.length; s++)
            {
                BattleResult result = Battle.simulate(combatant, moveset, meta.get(m).combatant(), meta.get(m).moveset(),
                    new ShieldScenario(SHIELDS[s], SHIELDS[s]));
                total += result.ratingA();
                won[m * SHIELDS.length + s] = result.winner() == Winner.A;
            }
        }
        return new Record(won, total / won.length);
    }
}

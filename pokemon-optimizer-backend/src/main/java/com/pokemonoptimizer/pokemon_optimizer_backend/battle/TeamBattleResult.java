package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import java.util.List;

public record TeamBattleResult(List<Integer> hpA, List<Integer> hpB)
{
    public Winner winner()
    {
        long aliveA = hpA.stream().filter(hp -> hp > 0).count();
        long aliveB = hpB.stream().filter(hp -> hp > 0).count();

        if (aliveA > aliveB)
        {
            return Winner.A;
        }
        else if (aliveA < aliveB)
        {
            return Winner.B;
        }
        else
        {
            return Winner.TIE;
        }
    }
}

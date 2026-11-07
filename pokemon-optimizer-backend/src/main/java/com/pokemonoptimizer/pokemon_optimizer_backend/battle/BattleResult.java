package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

public record BattleResult(int turns, int hpA, int hpB, int energyA, int energyB, 
    int shieldsA, int shieldsB,
    int atkStageA, int defStageA, int atkStageB, int defStageB,
    int ratingA, int ratingB) {

        public Winner winner()
        {
            if (ratingA > ratingB)
            {
                return Winner.A;
            } 
            else if (ratingA < ratingB)
            {
                return Winner.B;
            }
            else
            {
                return Winner.TIE;
            }
        }
    }

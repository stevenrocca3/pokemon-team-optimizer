package com.pokemonoptimizer.pokemon_optimizer_backend.battle;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;

public final class Battle
{
    private static final int MAX_ENERGY = 100;

    private Battle() {}

    private static final class Side 
    {
        final Combatant pokemon;
        final Move fastMove;
        int hp;
        int energy = 0;
        int freeOnTurn = 1;
        int landsOnTurn = 0;

        Side(Combatant pokemon, Move fastmove)
        {
            this.pokemon = pokemon;
            this.fastMove = fastmove;
            this.hp = pokemon.maxHp();
        }
    }

    public static BattleResult simulate(Combatant a, Move fastA, Combatant b, Move fastB)
    {
        Side sideA = new Side(a, fastA);
        Side sideB = new Side(b, fastB);

        int turn = 0;
        while (sideA.hp > 0 && sideB.hp > 0)
        {
            turn++;

            // phase 1: start moves (both sides)
            startMoveIfFree(sideA, turn);
            startMoveIfFree(sideB, turn);

            resolveIfLanding(sideA, sideB, turn);
            resolveIfLanding(sideB, sideA, turn);
        }
        return new BattleResult(turn, sideA.hp, sideB.hp, sideA.energy, sideB.energy);
    }

    private static void startMoveIfFree(Side side, int turn) 
    {
        if (side.freeOnTurn <= turn)
        {
            int n = side.fastMove.turns();
            side.landsOnTurn = turn + n - 1;
            side.freeOnTurn = turn + n;
        }
    }

    private static void resolveIfLanding(Side attacker, Side defender, int turn)
    {
        if (attacker.landsOnTurn == turn && defender.hp > 0)
        {
            int damage = DamageCalculator.damage(attacker.pokemon, defender.pokemon, attacker.fastMove);
            defender.hp = Math.max(0, defender.hp - damage);
            attacker.energy = Math.min(MAX_ENERGY, attacker.energy + attacker.fastMove.energyGain());
        }
    }
}

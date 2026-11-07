package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;

public final class Battle
{
    private static final int MAX_ENERGY = 100;

    private Battle() {}

    private static final class Side 
    {
        final Combatant pokemon;
        int shields;
        final Move fastMove;
        final Move chargedMove;
        boolean throwsCharged = false;
        boolean faintedByCharged = false;
        int hp;
        int energy = 0;
        int freeOnTurn = 1;
        int landsOnTurn = 0;

        Side(Combatant pokemon, Moveset moveset, int shields)
        {
            this.pokemon = pokemon;
            this.shields = shields;
            this.fastMove = moveset.fastMove();
            this.chargedMove = moveset.chargedMoves().isEmpty() ? null : moveset.chargedMoves().get(0);
            this.hp = pokemon.maxHp();
        }
    }

    public static BattleResult simulate(Combatant a, Moveset movesetA, Combatant b, Moveset movesetB)
    {
        return simulate(a, movesetA, b, movesetB, new ShieldScenario(0,0));
    }

    public static BattleResult simulate(Combatant a, Moveset movesetA, Combatant b, Moveset movesetB, ShieldScenario shields)
    {
        Side sideA = new Side(a, movesetA, shields.shieldsA());
        Side sideB = new Side(b, movesetB, shields.shieldsB());

        int turn = 0;
        while (sideA.hp > 0 && sideB.hp > 0)
        {
            turn++;

            // phase 1: start moves (both sides)
            startMoveIfFree(sideA, turn);
            startMoveIfFree(sideB, turn);
            if (sideA.throwsCharged && sideB.throwsCharged)
            {
                throw new IllegalStateException("CMP ties not implemented yet.");
            }

            // phase 2a: charged moves resolve first
            resolveCharged(sideA, sideB);
            resolveCharged(sideB, sideA);

            // phase 2b: fast moves
            resolveIfLanding(sideA, sideB, turn);
            resolveIfLanding(sideB, sideA, turn);

            // phase 2c: dead turn + floating fast moves
            if (sideA.throwsCharged || sideB.throwsCharged)
            {
                afterChargedTurn(sideA, turn);
                afterChargedTurn(sideB, turn);
            }
        }
        return new BattleResult(turn, sideA.hp, sideB.hp, sideA.energy, sideB.energy, sideA.shields, sideB.shields);
    }

    private static void startMoveIfFree(Side side, int turn) 
    {
        side.throwsCharged = false;
        if (side.freeOnTurn <= turn)
        {
            if (side.chargedMove != null && side.energy >= side.chargedMove.energy())
            {
                side.throwsCharged = true;
            } 
            else 
            {
                int n = side.fastMove.turns();
                side.landsOnTurn = turn + n - 1;
                side.freeOnTurn = turn + n;
            }
        }
    }

    private static void resolveIfLanding(Side attacker, Side defender, int turn)
    {
        if (attacker.landsOnTurn == turn && defender.hp > 0 && !(attacker.hp == 0 && attacker.faintedByCharged))
        {
            int damage = DamageCalculator.damage(attacker.pokemon, defender.pokemon, attacker.fastMove);
            defender.hp = Math.max(0, defender.hp - damage);
            attacker.energy = Math.min(MAX_ENERGY, attacker.energy + attacker.fastMove.energyGain());
        }
    }

    private static void resolveCharged(Side attacker, Side defender)
    {
        if (attacker.throwsCharged)
        {
            attacker.energy = Math.min(MAX_ENERGY, attacker.energy + attacker.chargedMove.energyGain());
            attacker.energy -= attacker.chargedMove.energy();
            int damage = DamageCalculator.damage(attacker.pokemon, defender.pokemon, attacker.chargedMove);
            if (defender.shields > 0)
            {
                defender.shields--;
                damage = 1;
            } 
            
            defender.hp = Math.max(0, defender.hp - damage);
            if (defender.hp == 0)
            {
                defender.faintedByCharged = true;
            }
        }
    }

    private static void afterChargedTurn(Side attacker, int turn)
    {
        attacker.freeOnTurn = turn + 2;
        if (attacker.landsOnTurn > turn)
        {
            attacker.landsOnTurn = turn + 1;
        }
    }
}

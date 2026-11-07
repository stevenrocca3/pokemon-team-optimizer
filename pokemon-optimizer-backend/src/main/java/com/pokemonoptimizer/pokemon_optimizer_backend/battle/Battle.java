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
        int atkStage;
        int defStage;
        double buffMeter;
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

        boolean usePriority = sideA.pokemon.attackStat() != sideB.pokemon.attackStat();
        Side first = sideB.pokemon.attackStat() > sideA.pokemon.attackStat() ? sideB : sideA;
        Side second = (first == sideA) ? sideB : sideA;
        while (sideA.hp > 0 && sideB.hp > 0)
        {
            turn++;

            // phase 1: start moves (both sides)
            startMoveIfFree(sideA, turn);
            startMoveIfFree(sideB, turn);

            // phase 2a: charged moves resolve first
            resolveCharged(first, second);
            if (!(usePriority && second.faintedByCharged))
            {
                resolveCharged(second, first);
            }

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

        int ratingA = rating(sideA, sideB);
        int ratingB = rating(sideB, sideA);
        return new BattleResult(turn, sideA.hp, sideB.hp, sideA.energy, sideB.energy, sideA.shields, sideB.shields, 
            sideA.atkStage, sideA.defStage, sideB.atkStage, sideB.defStage, ratingA, ratingB);
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
            int damage = DamageCalculator.damage(attacker.pokemon, attacker.atkStage, defender.pokemon, defender.defStage, attacker.fastMove);
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
            int damage = DamageCalculator.damage(attacker.pokemon, attacker.atkStage, defender.pokemon, defender.defStage, attacker.chargedMove);
            if (defender.shields > 0)
            {
                defender.shields--;
                damage = 1;
            } 
            
            defender.hp = Math.max(0, defender.hp - damage);
            applyBuffs(attacker, defender);
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

     private static void applyBuffs(Side attacker, Side defender)
     {
        Move move = attacker.chargedMove;
        if (move.buffs().isEmpty())
        {
            return;
        }
        boolean applies;
        if (move.buffApplyChance() >= 1.0)
        {
            applies = true;
        }
        else 
        {
            double start = Math.floor(attacker.buffMeter);
            attacker.buffMeter += move.buffApplyChance();
            applies = Math.floor(attacker.buffMeter) > start;
        }
        if (!applies)
        {
            return;
        }
        Side target;
        if (move.buffTarget().equals("self"))
        {
            target = attacker;
        } 
        else if (move.buffTarget().equals("opponent"))
        {
            target = defender;
        }
        else 
        {
            throw new IllegalStateException("unsupported buff target: " + move.buffTarget());
        }

        target.atkStage = StatStages.apply(target.atkStage, move.buffs().get(0));
        target.defStage = StatStages.apply(target.defStage, move.buffs().get(1));
     }

     private static int rating(Side self, Side opponent)
     {
        //return Math.floor( (500 * ((opponent.stats.hp - opponent.hp) /
        //  opponent.stats.hp)) + (500 * (self.hp / self.stats.hp)))
        double damageDealt = (opponent.pokemon.maxHp() - opponent.hp) / (double) opponent.pokemon.maxHp();
        double hpKept = self.hp / (double) self.pokemon.maxHp();
        return (int) Math.floor(500 * damageDealt + 500 * hpKept);
     }
}

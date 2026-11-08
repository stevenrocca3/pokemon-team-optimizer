package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;

/**
 * One Pokémon's copy of a move during a battle. Mirrors PvPoke's move object (GameMaster.getMoveById),
 * including the fields PvPoke caches and mutates while battling (damage, dpe, the buff meter), because
 * the decision AI reads those cached values rather than recomputing them.
 */
final class BattleMove
{
    final String moveId;
    final String type;
    final int power;
    final int energy;
    final int energyGain;
    final int cooldown;
    final int turns;

    /** null when the move has no buffs (JS undefined). The Zap Cannon clause can set or clear it. */
    int[] buffs;
    String buffTarget;
    double buffApplyChance;

    /** Deterministic chance-buff meter; null when PvPoke leaves it undefined. */
    Double buffApplyMeter;

    boolean selfDebuffing;
    boolean selfBuffing;
    boolean selfAttackDebuffing;
    boolean selfDefenseDebuffing;

    /** Last damage PvPoke computed for this move (initializeMove, useMove, wouldShield and timing checks overwrite it). */
    int damage;
    double dpe;

    BattleMove(Move move)
    {
        this.moveId = move.moveId();
        this.type = move.type();
        this.power = move.power();
        this.energy = move.energy();
        this.energyGain = move.energyGain();
        this.turns = move.turns();
        this.cooldown = move.turns() * 500;

        if (!move.buffs().isEmpty())
        {
            this.buffs = new int[] { move.buffs().get(0), move.buffs().get(1) };
            this.buffTarget = move.buffTarget();
            this.buffApplyChance = move.buffApplyChance();

            if ("both".equals(buffTarget))
            {
                throw new IllegalStateException("Moves that buff both sides are not supported yet: " + moveId);
            }

            // GameMaster.getMoveById flags
            if ("self".equals(buffTarget) && buffApplyChance >= .5 && !moveId.equals("DRAGON_ASCENT")
                && (buffs[0] < 0 || buffs[1] < 0))
            {
                selfDebuffing = true;
                selfAttackDebuffing = buffs[0] < 0;
                selfDefenseDebuffing = buffs[1] < 0;
            }

            if (buffApplyChance == 1 && ("opponent".equals(buffTarget)
                || ("self".equals(buffTarget) && (buffs[0] > 0 || buffs[1] > 0))))
            {
                selfBuffing = true;
            }
        }
    }

    boolean hasBuffs()
    {
        return buffs != null;
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import java.util.List;

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

    /** No minigame and can't be shielded (Gulp Missile). */
    final boolean instant;
    /** Still fires if its user was knocked out by the Charged Move that triggered it (Gulp Missile). */
    final boolean ignoresFaint;
    /** Damage is power% of the target's max HP, not the attack formula (Gulp Missile). */
    final boolean percentMaxHp;

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
        this.instant = move.hasTag("instant");
        this.ignoresFaint = move.hasTag("ignoresFaint");
        this.percentMaxHp = "percentMaxHP".equals(move.damageMethod());

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

    // Damage is computed constantly while the AI plans, and type lookups were most of the 3v3 run time.
    // Both answers only depend on a types list, so remember the last one; a different list just recomputes.
    private List<String> stabFor;
    private boolean stab;
    private List<String> effectivenessFor;
    private double effectiveness;

    boolean stabFor(List<String> attackerTypes)
    {
        if (attackerTypes != stabFor)
        {
            stab = attackerTypes.contains(type);
            stabFor = attackerTypes;
        }
        return stab;
    }

    double effectivenessAgainst(List<String> defenderTypes)
    {
        if (defenderTypes != effectivenessFor)
        {
            effectiveness = TypeChart.effectiveness(type, defenderTypes);
            effectivenessFor = defenderTypes;
        }
        return effectiveness;
    }

    boolean hasBuffs()
    {
        return buffs != null;
    }
}

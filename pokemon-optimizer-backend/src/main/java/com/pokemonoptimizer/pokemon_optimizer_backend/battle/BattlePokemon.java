package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import java.util.ArrayList;
import java.util.List;

/**
 * A Pokémon's mutable state during one battle, shaped like PvPoke's Pokemon object so the decision
 * AI can be ported line for line. Built fresh for every simulate() call.
 */
final class BattlePokemon
{
    private static final int BUFF_DIVISOR = 4;

    final Combatant combatant;
    final Moveset moveset;
    final int index;
    final List<String> types;
    final String speciesName;
    final String speciesId;
    /** PvPoke's activeFormId: the species id, or a form it changed into mid-battle (Cramorant's Gulping / Gorging). */
    String formId;
    /** The league's CP cap (PvPoke's battle.getCP()): Aegislash's Blade form stats depend on it. */
    int battleCp = Battle.NO_CP_CAP;
    private double[] bladeStats;

    /** PvPoke's stats.atk and stats.def: cpm x (base + IV), no Shadow or buffs. Aegislash's form changes them. */
    double statAtk;
    double statDef;
    final int maxHp;

    int hp;
    int energy = 0;
    int shields;
    /** 3v3: the team's shields when this Pokémon came in, and how many it has used this battle (PvPoke's startingShields, battleStats.shieldsUsed). */
    int startingShields;
    int shieldsUsed = 0;
    int cooldown = 0;
    final int[] statBuffs = { 0, 0 };
    String faintSource = "";
    double turnsToKO = -1;
    boolean hasActed = false;
    final int priority = 0;

    // PvPoke's per-Pokémon AI settings, at the website defaults
    int baitShields = 1;
    boolean optimizeMoveTiming = true;
    boolean farmEnergy = false;

    /** Aegislash swaps it when it changes form. */
    BattleMove fastMove;
    final List<BattleMove> chargedMoves;
    List<BattleMove> activeChargedMoves = new ArrayList<>();
    BattleMove fastestChargedMove;
    BattleMove bestChargedMove;

    BattlePokemon(Combatant combatant, Moveset moveset, int shields, int index)
    {
        this.combatant = combatant;
        this.moveset = moveset;
        this.index = index;
        this.types = combatant.species().types();
        this.speciesName = combatant.species().speciesName();
        this.speciesId = combatant.species().speciesId();
        this.formId = speciesId;
        this.statAtk = combatant.attackStat();
        this.statDef = combatant.defenseStat();
        this.maxHp = combatant.maxHp();
        this.hp = maxHp;
        this.shields = shields;
        this.startingShields = shields;
        this.fastMove = new BattleMove(moveset.fastMove());
        this.chargedMoves = new ArrayList<>(moveset.chargedMoves().size());
        for (var move : moveset.chargedMoves())
        {
            chargedMoves.add(new BattleMove(move));
        }
        Aegislash.markDefaultMoves(this);
    }

    /** A fresh Pokémon in this one's current state (HP, energy, shields, stat stages, cooldown), for side simulations. */
    BattlePokemon copy(int newIndex)
    {
        BattlePokemon copy = new BattlePokemon(combatant, moveset, shields, newIndex);
        copy.battleCp = battleCp;
        copy.hp = hp;
        copy.energy = energy;
        copy.cooldown = cooldown;
        copy.setStatBuffs(statBuffs);
        FormChanges.copyForm(this, copy);   // TrainingAI starts its side simulations in the current form (startFormId = activeFormId)
        return copy;
    }

    /** getEffectiveStat(0): stats.atk x (buff multiplier x Shadow multiplier). */
    double effectiveAttack()
    {
        return StatStages.multiplier(statBuffs[0]) * (combatant.shadow() ? Combatant.SHADOW_ATK : 1.0) * statAtk;
    }

    double effectiveDefense()
    {
        return StatStages.multiplier(statBuffs[1]) * (combatant.shadow() ? Combatant.SHADOW_DEF : 1.0) * statDef;
    }

    /** Aegislash's Blade form { atk, def }, worked out once (getFormStats only depends on level, IVs and the cap). */
    double[] bladeStats()
    {
        if (bladeStats == null)
        {
            bladeStats = Aegislash.formStats(this, Aegislash.BLADE);
        }
        return bladeStats;
    }

    void applyStatBuffs(int attackChange, int defenseChange)
    {
        statBuffs[0] = StatStages.apply(statBuffs[0], attackChange);
        statBuffs[1] = StatStages.apply(statBuffs[1], defenseChange);
    }

    void setStatBuffs(int[] values)
    {
        statBuffs[0] = values[0];
        statBuffs[1] = values[1];
    }

    int[] copyStatBuffs()
    {
        return new int[] { statBuffs[0], statBuffs[1] };
    }

    /** Pokemon.getBoostMove: the last guaranteed-enough buff move that isn't self debuffing, or null. */
    BattleMove boostMove()
    {
        BattleMove boost = null;
        for (BattleMove move : chargedMoves)
        {
            if (move.hasBuffs() && move.buffApplyChance >= 0.5 && !move.selfDebuffing)
            {
                boost = move;
            }
        }
        return boost;
    }

    /** Pokemon.initializeMove: cache stab-aware damage against the current opponent, and dpe. */
    private void initializeMove(BattleMove move, BattlePokemon opponent)
    {
        move.damage = ActionLogic.damage(this, opponent, move);

        if (move.energy > 0)
        {
            move.dpe = move.damage / (double) move.energy;

            if (move.hasBuffs())
            {
                double buffEffect = 0;
                if ("self".equals(move.buffTarget) && move.buffs[0] > 0)
                {
                    buffEffect = move.buffs[0] * (80.0 / move.energy);
                }
                else if ("opponent".equals(move.buffTarget) && move.buffs[1] < 0)
                {
                    buffEffect = Math.abs(move.buffs[1]) * (80.0 / move.energy);
                }

                double multiplier = 1;
                if (buffEffect > 0)
                {
                    multiplier = (BUFF_DIVISOR + (buffEffect * move.buffApplyChance)) / BUFF_DIVISOR;
                }
                move.dpe *= multiplier;
            }
        }
    }

    /** Pokemon.resetMoves: initialize moves, then order activeChargedMoves and pick fastest/best. */
    void resetMoves(BattlePokemon opponent)
    {
        initializeMove(fastMove, opponent);
        for (BattleMove move : chargedMoves)
        {
            initializeMove(move, opponent);
        }

        activeChargedMoves = new ArrayList<>(chargedMoves.size());
        if (chargedMoves.isEmpty())
        {
            bestChargedMove = null;
            return;
        }

        for (BattleMove move : chargedMoves)
        {
            if (move.hasBuffs() && move.buffApplyChance < 1)
            {
                move.buffApplyMeter = move.buffApplyChance;
                if (move.buffApplyChance == .5)
                {
                    move.buffApplyMeter = 0.0;
                }
            }
            activeChargedMoves.add(move);
        }

        // Stable sort by energy, like JS Array.sort
        activeChargedMoves.sort((a, b) -> Integer.compare(a.energy, b.energy));
        fastestChargedMove = activeChargedMoves.get(0);

        List<BattleMove> active = activeChargedMoves;
        if (active.size() > 1)
        {
            // Behavior for Aegislash to build energy in shield mode. PvPoke marks these inside the loop below, after
            // the Zap Cannon clause, but the marks stay on the moves and it resets moves more than once before a
            // battle starts, so every pass that counts already sees them; marking first gives the same result.
            // (Aegislash.markDefaultMoves covers the moves PvPoke marks even with one Charged Move.)
            if (Aegislash.SHIELD.equals(formId))
            {
                active.forEach(Aegislash::markSelfDebuffing);
            }

            for (int i = 1; i < active.size(); i++)
            {
                if (active.get(i).energy == active.get(0).energy && !active.get(i).selfDebuffing)
                {
                    if (active.get(i).hasBuffs() || active.get(i).damage > active.get(0).damage)
                    {
                        rotateFirstToEnd();
                    }
                }

                if (active.get(i).energy == active.get(0).energy && active.get(0).hasBuffs() && active.get(i).hasBuffs()
                    && !active.get(i).selfDebuffing && active.get(i).buffApplyChance > active.get(0).buffApplyChance)
                {
                    rotateFirstToEnd();
                }

                // The Zap Cannon Registeel clause
                if (active.get(0).moveId.equals("FOCUS_BLAST") && active.get(i).moveId.equals("ZAP_CANNON"))
                {
                    if (active.get(i).dpe - active.get(0).dpe > -.3)
                    {
                        active.get(0).buffs = new int[] { 0, 0 };
                        active.get(0).buffTarget = "self";
                        active.get(0).selfDebuffing = true;
                    }
                    else
                    {
                        active.get(0).buffs = null;
                        active.get(0).buffTarget = null;
                        active.get(0).selfDebuffing = false;
                    }
                }

                if (active.get(i).energy - active.get(0).energy <= 10 && !active.get(i).selfDebuffing)
                {
                    if (active.get(i).selfBuffing && active.get(0).dpe - active.get(i).dpe < .3)
                    {
                        rotateFirstToEnd();
                    }
                }

                if (active.get(i).energy - active.get(0).energy <= 10 && active.get(0).selfAttackDebuffing
                    && !active.get(i).selfDebuffing)
                {
                    rotateFirstToEnd();
                }

                if (active.get(i).energy - active.get(0).energy <= 10 && active.get(0).selfDebuffing
                    && active.get(0).energy > 50 && !active.get(i).selfDebuffing)
                {
                    rotateFirstToEnd();
                }

                if (active.get(i).energy - active.get(0).energy <= 5 && active.get(i).selfBuffing)
                {
                    rotateFirstToEnd();
                }
            }
        }

        bestChargedMove = active.get(0);
        bestChargedMove.dpe = bestChargedMove.damage / (double) bestChargedMove.energy;

        for (BattleMove move : active)
        {
            move.dpe = move.damage / (double) move.energy;

            if (((move.dpe - bestChargedMove.dpe > .03) && !move.moveId.equals("SUPER_POWER"))
                || (move.dpe - bestChargedMove.dpe > .3))
            {
                if (!bestChargedMove.selfBuffing || (move.dpe - bestChargedMove.dpe > .3))
                {
                    bestChargedMove = move;
                }
            }

            if (Math.abs(move.dpe - bestChargedMove.dpe) < .03 && bestChargedMove.hasBuffs() && move.hasBuffs()
                && move.buffApplyChance > bestChargedMove.buffApplyChance && !move.selfDebuffing)
            {
                bestChargedMove = move;
            }
        }
    }

    private void rotateFirstToEnd()
    {
        BattleMove first = activeChargedMoves.remove(0);
        activeChargedMoves.add(first);
    }
}

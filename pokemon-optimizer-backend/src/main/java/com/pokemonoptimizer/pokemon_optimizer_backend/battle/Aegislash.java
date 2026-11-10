package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import java.util.List;

import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.CpCalculator;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

/**
 * Aegislash's Stance Change, ported from PvPoke (Battle.useMove, DamageCalculator, ActionLogic, Pokemon.changeForm and
 * getFormStats; commit e02bde7).
 * <p>
 * Shield form is bulky: its Fast Moves do 1 damage and give 6 energy. Using a Charged Move switches it to Blade form
 * first (high Attack, low Defense, normal Fast Moves), and using a shield switches it back. Switching out also returns
 * it to Shield. Blade form's stats aren't Shield's at the same level: PvPoke works out a Blade level from the league
 * (about half the level in Great League, three quarters in Ultra), then lowers it until Blade fits under the CP cap.
 * <p>
 * The AI builds energy in Shield form instead of throwing early, saves shields while in Shield form against weak
 * moves, and treats every Charged Move as self debuffing (PvPoke's way to make it stack energy).
 */
final class Aegislash
{
    private Aegislash() {}

    static final String SHIELD = "aegislash_shield";
    static final String BLADE = "aegislash_blade";

    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final SpeciesType SHIELD_FORM = SPECIES.findForm(SHIELD).orElseThrow();
    private static final SpeciesType BLADE_FORM = SPECIES.findForm(BLADE).orElseThrow();

    /** Pokemon.getFormStats: { atk, def } of this Aegislash in that form, for its league's CP cap. */
    static double[] formStats(BattlePokemon poke, String formId)
    {
        SpeciesType form = BLADE.equals(formId) ? BLADE_FORM : SHIELD_FORM;
        Combatant c = poke.combatant;
        double level = c.level();

        if (!poke.speciesId.equals(formId))
        {
            if (BLADE.equals(formId))
            {
                if (poke.battleCp == 1500)
                {
                    level = Math.ceil(level * 0.5) + 1;
                }
                if (poke.battleCp == 2500)
                {
                    level = Math.ceil(level * 0.75);
                }
            }
            else
            {
                if (poke.battleCp == 1500)
                {
                    level = (level / 0.5) + 2;
                }
                if (poke.battleCp == 2500)
                {
                    level = Math.round(level / 0.75);
                }
            }
        }

        level = Math.min(level, 55);

        // Lower the new form's level a whole level at a time until it fits under the CP cap
        double[] stats = null;
        int cp = 0;
        while (stats == null || cp > poke.battleCp)
        {
            double cpm = cpm(level);
            if (Double.isNaN(cpm))
            {
                break;   // off PvPoke's CPM table: its loop stops there too
            }
            cp = (int) Math.floor(((form.baseAtk() + c.atkIv()) * Math.pow(form.baseDef() + c.defIv(), 0.5)
                * Math.pow(form.baseSta() + c.staIv(), 0.5) * Math.pow(cpm, 2)) / 10);
            stats = new double[] { cpm * (form.baseAtk() + c.atkIv()), cpm * (form.baseDef() + c.defIv()) };
            level -= 1;
        }
        if (stats == null)
        {
            throw new IllegalStateException("No level for " + formId + " under " + poke.battleCp + " CP");
        }
        return stats;
    }

    /**
     * Pokemon.changeForm for Aegislash: new Attack and Defense, and the Fast Move that goes with the form. (PvPoke's
     * resetMoves never refreshes Blade's Fast Move cached damage, since it isn't in the move pool; ours does. 3000
     * fixture battles came out the same either way, so the simpler version stays.)
     */
    static void setForm(BattlePokemon poke, String formId)
    {
        double[] stats = BLADE.equals(formId) ? poke.bladeStats() : formStats(poke, SHIELD);
        poke.formId = formId;
        poke.statAtk = stats[0];
        poke.statDef = stats[1];

        String fast = poke.fastMove.moveId;
        String swapped = BLADE.equals(formId) ? fast.replace("AEGISLASH_CHARGE_", "") : chargeVersion(fast);
        if (!swapped.equals(fast))
        {
            Move move = FormChanges.MOVES.findById(swapped).orElseThrow();
            poke.fastMove = new BattleMove(move);
        }
    }

    /** Pokemon.resetMoves in Shield form: every Charged Move counts as self debuffing, so the AI stacks energy. */
    static void markSelfDebuffing(BattleMove move)
    {
        move.buffs = new int[] { 0, 0 };
        move.buffTarget = "aegislash";     // PvPoke stores the Pokémon object here: neither "self" nor "opponent"
        move.buffApplyChance = Double.NaN;   // PvPoke leaves it undefined, so every comparison with it is false
        move.selfDebuffing = true;
    }

    /**
     * PvPoke's initialize gives every Pokémon the first two Charged Moves of its pool (sorted by id) and resets moves
     * before the real moveset is picked. For Aegislash that marks those two for good, so they stay self debuffing even
     * in a moveset where the other move isn't them, or where they're the only move.
     */
    static void markDefaultMoves(BattlePokemon poke)
    {
        if (!SHIELD.equals(poke.speciesId))
        {
            return;
        }
        List<String> pool = poke.combatant.species().learnset().chargedMoves().stream().sorted().toList();
        if (pool.size() < 2)
        {
            return;
        }
        for (BattleMove move : poke.chargedMoves)
        {
            if (pool.subList(0, 2).contains(move.moveId))
            {
                markSelfDebuffing(move);
            }
        }
    }

    private static String chargeVersion(String fast)
    {
        return switch (fast)
        {
            case "PSYCHO_CUT", "AIR_SLASH" -> "AEGISLASH_CHARGE_" + fast;
            default -> fast;
        };
    }

    /** Pokemon.getCPMByLevel; NaN past the table (PvPoke gets undefined there). */
    private static double cpm(double level)
    {
        int index = (int) Math.round(level * 2);
        return index >= 2 && index < CpCalculator.CPM_TABLE.length ? CpCalculator.CPM_TABLE[index] : Double.NaN;
    }
}

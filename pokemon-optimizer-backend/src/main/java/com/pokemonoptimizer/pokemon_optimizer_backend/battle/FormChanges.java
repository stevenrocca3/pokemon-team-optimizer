package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;

/**
 * Species that change form mid-battle, ported from PvPoke (Battle.useMove, ActionLogic, Pokemon.changeForm; commit
 * e02bde7). Cramorant and Aegislash have their own classes; this holds Mimikyu and Morpeko, and what all of them share.
 * <p>
 * Mimikyu: its Disguise blocks the first Charged Move that hits it unshielded, like a free shield (1 damage). It is
 * then Busted for the rest of the battle, switching included, with -1 Defense.
 * <p>
 * Morpeko: after its first Charged Move it is Hangry, and Aura Wheel becomes Dark instead of Electric. In the game it
 * flips back with every Charged Move; PvPoke's simulator leaves it Hangry until it switches out, and this matches
 * PvPoke. Its forms share stats and types, so only the move changes.
 */
final class FormChanges
{
    private FormChanges() {}

    static final MoveLookup MOVES = new MoveLookup();

    static final String MIMIKYU = "mimikyu";
    static final String MIMIKYU_BUSTED = "mimikyu_busted";
    static final String MORPEKO_FULL_BELLY = "morpeko_full_belly";
    static final String MORPEKO_HANGRY = "morpeko_hangry";

    private static final Move AURA_WHEEL_ELECTRIC = MOVES.findById("AURA_WHEEL_ELECTRIC").orElseThrow();
    private static final Move AURA_WHEEL_DARK = MOVES.findById("AURA_WHEEL_DARK").orElseThrow();

    /** Mimikyu with its Disguise up: the next unshielded Charged Move is blocked. */
    static boolean isDisguised(BattlePokemon poke)
    {
        return MIMIKYU.equals(poke.formId);
    }

    /** The Disguise breaks: Busted form, whose native -1 Defense applies on top of current stages (changeForm). */
    static void bustDisguise(BattlePokemon mimikyu)
    {
        mimikyu.formId = MIMIKYU_BUSTED;
        mimikyu.applyStatBuffs(0, -1);
    }

    /** Battle.useMove's post-attack form change for a "charged_move" trigger: Morpeko goes Hangry. True if it changed. */
    static boolean afterChargedMove(BattlePokemon attacker)
    {
        if (MORPEKO_FULL_BELLY.equals(attacker.formId))
        {
            setMorpekoForm(attacker, MORPEKO_HANGRY);
            return true;
        }
        return false;
    }

    /** Switching out: forms with resetOnSwitch go back to the original (Cramorant, Morpeko, Aegislash). Mimikyu stays Busted. */
    static void revertOnSwitch(BattlePokemon outgoing)
    {
        if (MORPEKO_HANGRY.equals(outgoing.formId))
        {
            setMorpekoForm(outgoing, MORPEKO_FULL_BELLY);
        }
        else if (Aegislash.BLADE.equals(outgoing.formId))
        {
            Aegislash.setForm(outgoing, Aegislash.SHIELD);
        }
        else if (Cramorant.isLoaded(outgoing))
        {
            outgoing.formId = outgoing.speciesId;
        }
    }

    /** A copy for side simulations takes the original's form, including the moves that come with it. */
    static void copyForm(BattlePokemon from, BattlePokemon to)
    {
        if (MORPEKO_HANGRY.equals(from.formId))
        {
            setMorpekoForm(to, MORPEKO_HANGRY);
        }
        else if (Aegislash.BLADE.equals(from.formId))
        {
            Aegislash.setForm(to, Aegislash.BLADE);
        }
        else
        {
            to.formId = from.formId;
        }
    }

    /** changeForm's replaceMove: Aura Wheel swaps type in the same slot. */
    private static void setMorpekoForm(BattlePokemon morpeko, String formId)
    {
        morpeko.formId = formId;
        Move from = MORPEKO_HANGRY.equals(formId) ? AURA_WHEEL_ELECTRIC : AURA_WHEEL_DARK;
        Move to = MORPEKO_HANGRY.equals(formId) ? AURA_WHEEL_DARK : AURA_WHEEL_ELECTRIC;
        for (int i = 0; i < morpeko.chargedMoves.size(); i++)
        {
            if (morpeko.chargedMoves.get(i).moveId.equals(from.moveId()))
            {
                morpeko.chargedMoves.set(i, new BattleMove(to));
            }
        }
    }
}

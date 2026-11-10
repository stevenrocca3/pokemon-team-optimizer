package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;

/**
 * Cramorant's form change, ported from PvPoke (Battle.useMove, ActionLogic, Pokemon.changeForm; commit e02bde7).
 * <p>
 * After it uses Surf or Dive, Cramorant becomes Gulping (above half HP) or Gorging (half or below). When a Charged
 * Move then hits it unshielded, it fires Gulp Missile right away: an instant move nobody can shield, doing 15% of the
 * attacker's max HP and lowering its Defense (Arrokuda) or Attack (Pikachu), even if that hit knocked Cramorant out.
 * Firing it turns Cramorant back to normal, and so does switching out. The forms share Cramorant's stats and types,
 * so a form change is only the form id.
 */
final class Cramorant
{
    private Cramorant() {}

    static final String SPECIES = "cramorant";
    static final String GULPING = "cramorant_gulping";
    static final String GORGING = "cramorant_gorging";

    private static final Move ARROKUDA = FormChanges.MOVES.findById("GULP_MISSILE_ARROKUDA").orElseThrow();
    private static final Move PIKACHU = FormChanges.MOVES.findById("GULP_MISSILE_PIKACHU").orElseThrow();

    static boolean isLoaded(BattlePokemon poke)
    {
        return GULPING.equals(poke.formId) || GORGING.equals(poke.formId);
    }

    /** Battle.useMove's post-attack form change: the form the attacker takes after this Charged Move, or null. */
    static String formAfter(BattlePokemon attacker, BattleMove move)
    {
        if (SPECIES.equals(attacker.formId) && (move.moveId.equals("DIVE") || move.moveId.equals("SURF")))
        {
            return attacker.hp / (double) attacker.maxHp > 0.5 ? GULPING : GORGING;
        }
        if (GULPING.equals(attacker.formId) && move.moveId.equals(ARROKUDA.moveId()))
        {
            return SPECIES;
        }
        if (GORGING.equals(attacker.formId) && move.moveId.equals(PIKACHU.moveId()))
        {
            return SPECIES;
        }
        return null;
    }

    /** The Gulp Missile a loaded Cramorant fires when hit, as a fresh move (PvPoke's extraChargedMovePool entry). */
    static BattleMove gulpMissile(BattlePokemon cramorant)
    {
        return new BattleMove(GULPING.equals(cramorant.formId) ? ARROKUDA : PIKACHU);
    }
}

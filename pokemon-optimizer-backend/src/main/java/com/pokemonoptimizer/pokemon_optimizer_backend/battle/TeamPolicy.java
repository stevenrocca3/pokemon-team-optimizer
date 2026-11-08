package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

/** How one side plays a 3v3: when to switch, and who comes in after a faint. */
interface TeamPolicy
{
    /** chooseAction: keep fighting and let ActionLogic pick the move. */
    int FIGHT = -1;
    /** chooseAction: use the fast move whatever ActionLogic would do (TrainingAI's overfarming). */
    int FAST_MOVE = -2;

    /** A new matchup started, a Charged Move landed, or the switch timer ran out (TrainingAI.evaluateMatchup). */
    default void evaluateMatchup(TeamView me, TeamView opponent) {}

    /** On this side's turn: a team index to switch to, FIGHT or FAST_MOVE. */
    int chooseAction(int turn, TeamView me, TeamView opponent);

    /** Shield decisions: let the 1v1 rule (ActionLogic) decide, or decide here. */
    enum Shield { DEFAULT, SHIELD, TAKE_IT }

    /** me.active() is about to be hit by a Charged Move; attackerEnergy is before paying for it. */
    default Shield decideShield(TeamView me, TeamView attacker, BattleMove move, int attackerEnergy)
    {
        return Shield.DEFAULT;
    }

    /** After me.active() faints: a team index to bring in, or -1 if nobody is left. */
    int chooseReplacement(TeamView me, TeamView opponent);
}

package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import java.util.ArrayList;
import java.util.List;

/**
 * A 1v1 battle, ported from PvPoke's Battle.step / getTurnAction / processAction / useMove
 * (src/js/battle/Battle.js, commit e02bde7) in "simulate" mode, so results match PvPoke's.
 * Decisions come from {@link ActionLogic}.
 */
public final class Battle
{
    private Battle() {}

    public static BattleResult simulate(Combatant a, Moveset movesetA, Combatant b, Moveset movesetB)
    {
        return simulate(a, movesetA, b, movesetB, new ShieldScenario(0, 0));
    }

    public static BattleResult simulate(Combatant a, Moveset movesetA, Combatant b, Moveset movesetB, ShieldScenario shields)
    {
        return simulate(a, movesetA, b, movesetB, shields, true);
    }

    /** optimizeMoveTiming is PvPoke's per-Pokémon setting (on by default on the site). */
    static BattleResult simulate(Combatant a, Moveset movesetA, Combatant b, Moveset movesetB, ShieldScenario shields,
        boolean optimizeMoveTiming)
    {
        BattlePokemon pokeA = new BattlePokemon(a, movesetA, shields.shieldsA(), 0);
        BattlePokemon pokeB = new BattlePokemon(b, movesetB, shields.shieldsB(), 1);
        pokeA.optimizeMoveTiming = optimizeMoveTiming;
        pokeB.optimizeMoveTiming = optimizeMoveTiming;

        Engine engine = new Engine(pokeA, pokeB);
        engine.run();

        return new BattleResult(engine.lastProcessedTurn, pokeA.hp, pokeB.hp, pokeA.energy, pokeB.energy,
            pokeA.shields, pokeB.shields, pokeA.statBuffs[0], pokeA.statBuffs[1], pokeB.statBuffs[0], pokeB.statBuffs[1],
            rating(pokeA, pokeB), rating(pokeB, pokeA));
    }

    private static int rating(BattlePokemon self, BattlePokemon opponent)
    {
        double damageDealt = (opponent.maxHp - opponent.hp) / (double) opponent.maxHp;
        double hpKept = self.hp / (double) self.maxHp;
        return (int) Math.floor(500 * damageDealt + 500 * hpKept);
    }

    /** PvPoke's TimelineAction, limited to fast and charged actions. */
    private static final class Action
    {
        final boolean charged;
        final int actor;
        final int turn;
        final int value; // index into chargedMoves
        int priority;
        boolean valid = false;
        boolean processed = false;

        Action(boolean charged, int actor, int turn, int value, int priority)
        {
            this.charged = charged;
            this.actor = actor;
            this.turn = turn;
            this.value = value;
            this.priority = priority;
        }
    }

    /** The battle state PvPoke keeps on its Battle object. ActionLogic reads turns and the action queue. */
    static final class Engine
    {
        private static final int DELTA_TIME = 500;
        private static final int CHARGED_MINIGAME_TIME = 10000;
        private static final int MAX_TIME = 240000;

        private final BattlePokemon[] pokemon;
        private final boolean usePriority;

        int turns = 1;
        int lastProcessedTurn = 0;
        private int time = 0;
        private List<Action> queuedActions = new ArrayList<>();
        private List<Action> previousTurnActions = new ArrayList<>();
        private int roundChargedMoveUsed;
        private boolean roundShieldUsed;

        Engine(BattlePokemon a, BattlePokemon b)
        {
            this.pokemon = new BattlePokemon[] { a, b };
            a.resetMoves(b);
            b.resetMoves(a);
            this.usePriority = a.statAtk != b.statAtk;
        }

        /** Fast actions this Pokémon has queued but not yet resolved. */
        int queuedFastMoves(int actor)
        {
            int count = 0;
            for (Action action : queuedActions)
            {
                if (action.actor == actor && !action.charged)
                {
                    count++;
                }
            }
            return count;
        }

        void run()
        {
            boolean continueBattle = true;
            while (continueBattle)
            {
                step();
                continueBattle = pokemon[0].hp > 0 && pokemon[1].hp > 0;
                if (time > MAX_TIME)
                {
                    continueBattle = false;
                }
            }
        }

        private void step()
        {
            roundChargedMoveUsed = 0;
            roundShieldUsed = false;
            List<Action> turnActions = new ArrayList<>();

            // Reduce cooldown for both Pokemon
            for (BattlePokemon poke : pokemon)
            {
                poke.cooldown = Math.max(0, poke.cooldown - DELTA_TIME);
                poke.hasActed = false;
            }

            boolean chargedMoveLastTurn = previousTurnActions.stream().anyMatch(action -> action.charged);
            // Cooldowns are set after decisions, because decisions read the current cooldowns
            int[] cooldownsToSet = { pokemon[0].cooldown, pokemon[1].cooldown };

            for (int i = 0; i < 2; i++)
            {
                BattlePokemon poke = pokemon[i];
                BattlePokemon opponent = pokemon[1 - i];
                Action action = getTurnAction(poke, opponent);

                if (action == null)
                {
                    continue;
                }
                // The turn after a Charged Move: the decision still ran (side effects included) but is dropped
                if (chargedMoveLastTurn)
                {
                    continue;
                }
                if (poke.hp > 0 && opponent.hp > 0)
                {
                    cooldownsToSet[i] += action.charged ? 500 : poke.fastMove.cooldown;
                    queuedActions.add(action);
                }
            }

            pokemon[0].cooldown = cooldownsToSet[0];
            pokemon[1].cooldown = cooldownsToSet[1];

            // Take actions from the queue that are ready this turn
            for (int i = 0; i < queuedActions.size(); i++)
            {
                Action action = queuedActions.get(i);
                int timeSinceActivated = (turns - action.turn) * 500;
                int requiredTimeToPass = action.charged ? 0 : pokemon[action.actor].fastMove.cooldown - 500;

                // Floating Fast Moves: after a Charged Move, a Fast Move in progress lands right away
                if (timeSinceActivated >= requiredTimeToPass || (!action.charged && chargedMoveLastTurn && action.turn < turns))
                {
                    turnActions.add(action);
                    queuedActions.remove(i);
                    i--;
                }
            }

            // Stable sort, highest priority first
            turnActions.sort((x, y) -> Integer.compare(y.priority, x.priority));

            for (Action action : turnActions)
            {
                BattlePokemon poke = pokemon[action.actor];
                BattlePokemon opponent = pokemon[1 - action.actor];

                if (action.charged)
                {
                    BattleMove move = poke.chargedMoves.get(action.value);
                    if (poke.energy >= move.energy)
                    {
                        action.valid = true;
                    }
                    // Knocked out by a Charged Move that went first
                    if (usePriority && poke.hp <= 0 && "charged".equals(poke.faintSource))
                    {
                        action.valid = false;
                    }
                }
                else
                {
                    action.valid = true;
                    if (opponent.hp < 1)
                    {
                        action.valid = false;
                    }
                    if (poke.hp < 1 && "charged".equals(poke.faintSource))
                    {
                        action.valid = false;
                    }
                }

                processAction(action, poke, opponent);
            }

            previousTurnActions = turnActions;

            if (roundChargedMoveUsed == 0)
            {
                time += DELTA_TIME;
            }
            else if (roundShieldUsed)
            {
                time += CHARGED_MINIGAME_TIME * (roundChargedMoveUsed - 1);
            }
            else
            {
                time += CHARGED_MINIGAME_TIME;
            }

            lastProcessedTurn = turns;
            turns++;

            // Reset after a charged move
            if (roundChargedMoveUsed > 0)
            {
                for (BattlePokemon poke : pokemon)
                {
                    poke.cooldown = 500;
                }
            }
        }

        private Action getTurnAction(BattlePokemon poke, BattlePokemon opponent)
        {
            if (poke.cooldown != 0 || poke.hasActed)
            {
                return null;
            }
            poke.hasActed = true;

            Integer chargedIndex = ActionLogic.decideAction(this, poke, opponent);
            if (chargedIndex == null)
            {
                return new Action(false, poke.index, turns, 0, poke.priority);
            }

            int priority = poke.priority + 10;
            if (poke.statAtk > opponent.statAtk)
            {
                priority++;
            }
            return new Action(true, poke.index, turns, chargedIndex, priority);
        }

        private void processAction(Action action, BattlePokemon poke, BattlePokemon opponent)
        {
            if (!action.valid || action.processed)
            {
                return;
            }
            action.processed = true;

            if (!action.charged)
            {
                useMove(poke, opponent, poke.fastMove, false);
                return;
            }

            BattleMove move = poke.chargedMoves.get(action.value);
            if (poke.energy >= move.energy)
            {
                useMove(poke, opponent, move, true);
                roundChargedMoveUsed++;
            }
        }

        private void useMove(BattlePokemon attacker, BattlePokemon defender, BattleMove move, boolean charged)
        {
            int damage = ActionLogic.damage(attacker, defender, move);
            move.damage = damage;

            if (charged)
            {
                attacker.energy -= move.energy;

                if (usePriority && roundChargedMoveUsed > 0 && !roundShieldUsed)
                {
                    time += CHARGED_MINIGAME_TIME;
                }

                if (defender.shields > 0)
                {
                    if (shouldShield(attacker, defender, move, damage))
                    {
                        damage = 1;
                        defender.shields--;
                        roundShieldUsed = true;

                        if (roundChargedMoveUsed == 0)
                        {
                            time += CHARGED_MINIGAME_TIME;
                        }
                    }
                }
            }
            else
            {
                attacker.energy = Math.min(100, attacker.energy + attacker.fastMove.energyGain);
            }

            defender.hp = Math.max(0, defender.hp - damage);

            applyBuffs(attacker, defender, move);

            if (defender.hp <= 0)
            {
                defender.faintSource = charged ? "charged" : "fast";
            }
        }

        /** The defender shields by default; these PvPoke exceptions defer to ActionLogic.wouldShield. */
        private boolean shouldShield(BattlePokemon attacker, BattlePokemon defender, BattleMove move, int damage)
        {
            boolean useShield = true;
            boolean wouldShield = ActionLogic.wouldShield(this, attacker, defender, move);

            // Don't shield early PUP's, Acid Sprays, or similar moves
            if (move.hasBuffs() && move.selfBuffing)
            {
                if (("self".equals(move.buffTarget) && move.buffs[0] > 0) || ("opponent".equals(move.buffTarget) && move.buffs[1] < 0))
                {
                    useShield = wouldShield;
                }
            }

            // Don't shield early moves if the defender has a defense debuffing move
            if (defender.bestChargedMove != null && defender.bestChargedMove.selfDefenseDebuffing)
            {
                if (attacker.shields > 0)
                {
                    useShield = wouldShield;
                }
                else if (attacker.bestChargedMove != null)
                {
                    // Shield this attack if the defender's next move will knock out the attacker
                    double fastToNextCharged = Math.ceil((defender.bestChargedMove.energy - defender.energy) / (double) defender.fastMove.energyGain);
                    double turnsToNextCharged = fastToNextCharged * defender.fastMove.turns;
                    double cycleDamage = (fastToNextCharged * defender.fastMove.damage) + defender.bestChargedMove.damage;

                    double attackerTurnsToNextCharged = Math.ceil((attacker.activeChargedMoves.get(0).energy - attacker.energy)
                        / (double) attacker.fastMove.energyGain) * attacker.fastMove.turns;

                    if (attacker.statAtk > defender.statAtk)
                    {
                        attackerTurnsToNextCharged--;
                    }

                    if (turnsToNextCharged >= attackerTurnsToNextCharged && attacker.hp <= cycleDamage)
                    {
                        useShield = wouldShield;
                    }
                }
            }

            return useShield;
        }

        /** Guaranteed buffs always apply; chance buffs use PvPoke's deterministic meter. Shields don't stop them. */
        private static void applyBuffs(BattlePokemon attacker, BattlePokemon defender, BattleMove move)
        {
            if (!move.hasBuffs())
            {
                return;
            }

            boolean applies = move.buffApplyChance == 1;
            if (move.buffApplyChance < 1 && move.buffApplyMeter != null)
            {
                double start = Math.floor(move.buffApplyMeter);
                move.buffApplyMeter += move.buffApplyChance;
                if (start < Math.floor(move.buffApplyMeter))
                {
                    applies = true;
                }
            }

            if (!applies)
            {
                return;
            }

            BattlePokemon target = "self".equals(move.buffTarget) ? attacker : defender;
            target.applyStatBuffs(move.buffs[0], move.buffs[1]);
        }
    }
}

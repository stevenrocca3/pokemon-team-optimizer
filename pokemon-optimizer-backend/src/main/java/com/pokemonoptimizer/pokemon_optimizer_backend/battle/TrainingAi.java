package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PvPoke's 3v3 practice bot at Champion level (src/js/training/TrainingAI.js, commit e02bde7), made deterministic.
 *
 * Deliberate differences from PvPoke, all because PvPoke's bot is built to be a varied practice opponent:
 * - Where PvPoke draws a weighted random option (chooseOption), this takes the option with the highest total
 *   weight, first on ties. Overfarming (a 1-in-n roll) happens when it is more likely than not.
 * - Champion's energyGuessRange is 0 and reactionTime is 0, so nothing else here is random or time based.
 * - Side simulations of a benched Pokémon give it the team's real shields; PvPoke uses the stale 2 it was built with.
 * - PvPoke re-runs resetMoves before every decision, which also resets the chance-buff meter; this keeps the meter.
 *
 * One instance per side: it remembers its current strategy between turns.
 */
final class TrainingAi implements TeamPolicy
{
    private static final int[] SHIELD_WEIGHTS = { 4, 4, 1 };

    /** The team's position 1, which a voluntary switch-out goes to while it's alive. */
    static final int SAFE_SWAP = 1;

    /** Our side's lineup: switching out goes to the safe swap. Faints still bring in the best matchup. */
    private final boolean followsRoles;

    TrainingAi()
    {
        this(false);
    }

    TrainingAi(boolean followsRoles)
    {
        this.followsRoles = followsRoles;
    }

    private String currentStrategy = "DEFAULT";
    private String previousStrategy = "DEFAULT";
    private double noBaitAverage = 0; // scenarios.noBait.average from the last evaluation

    // ---- evaluateMatchup: pick a strategy for this matchup ----

    @Override
    public void evaluateMatchup(TeamView me, TeamView them)
    {
        BattlePokemon pokemon = me.active();
        BattlePokemon opponent = them.active();

        pokemon.baitShields = 1;
        pokemon.farmEnergy = false;
        opponent.baitShields = 1;
        opponent.farmEnergy = false;

        double bothBait = scenario("BOTH_BAIT", pokemon, me.shields(), opponent);
        double neitherBait = scenario("NEITHER_BAIT", pokemon, me.shields(), opponent);
        double noBait = scenario("NO_BAIT", pokemon, me.shields(), opponent);
        double farm = scenario("FARM", pokemon, me.shields(), opponent);
        noBaitAverage = noBait;

        double overallRating = (bothBait + neitherBait + noBait) / 3;
        Map<String, Long> options = new LinkedHashMap<>(); // name -> total weight; chooseOption buckets by name
        long totalSwitchWeight = 0;

        if (me.switchTimer() == 0 && me.remaining() > 1)
        {
            int switchThreshold = 500;
            if (them.remaining() > 1)
            {
                switchThreshold = 450; // PRESERVE_SWITCH_ADVANTAGE
            }

            long switchWeight = (long) Math.floor(Math.max((switchThreshold - overallRating) / 10, 0));
            boolean canCounterSwitch = false;

            // Is the opponent switch locked and do I have a better Pokemon for it?
            if (them.switchTimer() > 30000)
            {
                for (BattlePokemon teammate : me.team())
                {
                    if (teammate.hp > 0 && teammate != pokemon)
                    {
                        double rating = scenario("NO_BAIT", teammate, me.shields(), opponent);
                        if (rating - 500 > (Math.max(overallRating, 500) - 500) * 1.2)
                        {
                            switchWeight += Math.round((rating - overallRating) / 10);
                            canCounterSwitch = true;
                        }
                    }
                }
            }

            // Don't switch Pokemon when HP is low (PRESERVE_SWITCH_ADVANTAGE; PvPoke compares ms with 30)
            if (them.switchTimer() - me.switchTimer() < 30 && pokemon.hp / (double) pokemon.maxHp <= .25 && them.remaining() > 1)
            {
                switchWeight = 0;
            }
            add(options, "SWITCH_BASIC", switchWeight);
            totalSwitchWeight += switchWeight;

            // SWITCH_FARM: see if it's feasible to build up energy before switching
            double dpt = opponent.fastMove.damage / (opponent.fastMove.cooldown / 500.0);
            double percentPerTurn = (dpt / pokemon.maxHp) * 100; // % of my HP the opponent's fast move deals per turn
            double weightFactor = Math.pow(Math.round(Math.max(3 - percentPerTurn, 0)), 2);

            if ("SWITCH_FARM".equals(previousStrategy))
            {
                weightFactor = 0; // switch immediately if previously failed to switch before a Charged Move
            }
            if (percentPerTurn > 3)
            {
                weightFactor = 0;
            }
            if (opponent.fastMove.energyGain / (opponent.fastMove.cooldown / 500.0) < 3)
            {
                weightFactor = 0;
            }
            if (pokemon.hp / (double) pokemon.maxHp < .5 && them.remaining() > 1)
            {
                weightFactor = .1;
            }
            if (canCounterSwitch)
            {
                weightFactor = .1;
            }
            if (pokemon.hp / (double) pokemon.maxHp < .25)
            {
                weightFactor = 0;
            }
            totalSwitchWeight += switchWeight * weightFactor;
            add(options, "SWITCH_FARM", (long) Math.floor(switchWeight * weightFactor));
        }

        // If there's a decent chance this Pokemon really shouldn't switch out, add other actions
        if (totalSwitchWeight < 10)
        {
            add(options, "DEFAULT", 1);

            if (opponent.shields > 0 && pokemon.chargedMoves.size() > 1)
            {
                long baitWeight = Math.max(Math.round((bothBait - noBait) / 20), 1);

                // If this Pokemon's moves are very close in DPE, prefer the shorter energy move
                if (bothBait >= 500 && pokemon.chargedMoves.size() == 2
                    && Math.abs(pokemon.chargedMoves.get(0).dpe - pokemon.chargedMoves.get(1).dpe) <= .1)
                {
                    baitWeight += 5;
                }
                if (me.shields() < them.shields())
                {
                    baitWeight += 2; // behind on shields, bait more
                }
                if (overallRating < 250)
                {
                    baitWeight = 1;
                }
                if ("SKULL_BASH".equals(pokemon.bestChargedMove.moveId) && me.remaining() > 1)
                {
                    baitWeight = 0;
                }
                if (pokemon.hp / (double) pokemon.maxHp < .25 && pokemon.energy < 70)
                {
                    baitWeight = 0;
                }
                add(options, "BAIT_SHIELDS", baitWeight);

                // If this Pokemon's most powerful Charged Move doesn't threaten shields, be less likely to bait
                int biggestDamage = 0;
                for (BattleMove move : pokemon.chargedMoves)
                {
                    biggestDamage = Math.max(biggestDamage, move.damage);
                }
                if (biggestDamage < pokemon.hp * .9 && pokemon.boostMove() == null)
                {
                    add(options, "DEFAULT", (long) Math.ceil(pokemon.hp / (double) biggestDamage));
                }
            }

            // FARM_ENERGY
            long farmWeight = Math.round((farm - 600) / 20);
            if (opponent.energy < 20 && farm > 500)
            {
                farmWeight += 12; // farm if we'll win and the opponent is low on energy
            }
            if (them.remaining() < 2)
            {
                farmWeight = 0; // don't farm against the last Pokemon
            }
            if (farmWeight >= 15)
            {
                farmWeight *= 5; // BAD_DECISION_PROTECTION
            }
            add(options, "FARM", farmWeight);
        }
        processStrategy(chooseOption(options), pokemon);
    }

    private static void add(Map<String, Long> options, String name, long weight)
    {
        options.merge(name, Math.max(weight, 0), Long::sum);
    }

    /** chooseOption, deterministic: the highest total weight, first on ties; all zero -> the first option. */
    private static String chooseOption(Map<String, Long> options)
    {
        String best = options.keySet().iterator().next();
        long bestWeight = 0;
        for (Map.Entry<String, Long> option : options.entrySet())
        {
            if (option.getValue() > bestWeight)
            {
                best = option.getKey();
                bestWeight = option.getValue();
            }
        }
        return best;
    }

    private void processStrategy(String strategy, BattlePokemon pokemon)
    {
        previousStrategy = currentStrategy;
        currentStrategy = strategy;

        switch (strategy)
        {
            case "SWITCH_FARM", "FARM" -> pokemon.farmEnergy = true;
            case "DEFAULT" ->
            {
                pokemon.baitShields = 0;
                pokemon.farmEnergy = false;
            }
            case "BAIT_SHIELDS" ->
            {
                pokemon.baitShields = 1;
                pokemon.farmEnergy = false;
            }
            default -> { }
        }
    }

    String strategy()
    {
        return currentStrategy;
    }

    // ---- decideAction: switch now, overfarm, or leave it to ActionLogic ----

    @Override
    public int chooseAction(int turn, TeamView me, TeamView them)
    {
        BattlePokemon poke = me.active();
        BattlePokemon opponent = them.active();

        // How much potential damage will they have after one more Fast Move?
        int extraFastMoves = Math.floorDiv(poke.fastMove.cooldown - opponent.cooldown, opponent.fastMove.cooldown);
        if (!currentStrategy.contains("SWITCH"))
        {
            extraFastMoves++; // some extra room for overfarming
        }
        if (opponent.cooldown > 0 && opponent.cooldown < poke.fastMove.cooldown)
        {
            extraFastMoves = Math.max(extraFastMoves, 1);
        }
        int futureEnergy = opponent.energy + extraFastMoves * opponent.fastMove.energyGain;
        int futureDamage = potentialDamage(opponent, poke, futureEnergy);

        // Stop farming if they're about to get a lethal Charged Move
        if (futureDamage >= poke.hp && "FARM".equals(currentStrategy))
        {
            processStrategy("DEFAULT", poke);
        }

        if (currentStrategy.contains("SWITCH") && me.switchTimer() == 0)
        {
            boolean performSwitch = "SWITCH_BASIC".equals(currentStrategy); // reactionTime 0
            if ("SWITCH_FARM".equals(currentStrategy))
            {
                // Is the opposing Pokemon close to a damaging Charged Move?
                performSwitch = futureDamage >= poke.hp || futureDamage >= poke.maxHp * .14 || poke.hp / (double) poke.maxHp < .2;
            }
            if (performSwitch)
            {
                return switchTarget(me, them);
            }
        }

        // OVERFARM: farm more energy than needed
        if (noBaitAverage > 450 && !currentStrategy.contains("SWITCH") && them.remaining() > 1)
        {
            long overfarmChance = 2 + Math.round((50 - opponent.energy) / 10.0); // likelier if the opponent is low on energy
            if (futureDamage >= poke.hp || futureDamage >= poke.maxHp * .15 || poke.energy == 100 || poke.hp / (double) poke.maxHp < .2)
            {
                overfarmChance = -1;
            }
            for (BattleMove move : poke.chargedMoves)
            {
                if (move.moveId.equals("POWER_UP_PUNCH") || move.moveId.equals("ACID_SPRAY"))
                {
                    overfarmChance = -1;
                }
            }
            // PvPoke overfarms when floor(random * chance) > 0, a (chance - 1) / chance shot: more likely than not from 3
            if (overfarmChance >= 3)
            {
                return FAST_MOVE;
            }
        }
        return FIGHT;
    }

    /** calculatePotentialDamage, stacking: the most damage one Charged Move type deals with this much energy. */
    private static int potentialDamage(BattlePokemon attacker, BattlePokemon defender, int energy)
    {
        int best = 0;
        for (BattleMove move : attacker.chargedMoves)
        {
            best = Math.max(best, (energy / move.energy) * ActionLogic.damage(attacker, defender, move));
        }
        return best;
    }

    // ---- decideShield ----

    @Override
    public Shield decideShield(TeamView me, TeamView them, BattleMove thrown, int attackerEnergy)
    {
        BattlePokemon defender = me.active();
        BattlePokemon attacker = them.active();

        // First, how hot are we looking in this current matchup?
        double currentRating = scenario("NO_BAIT", defender, me.shields(), attacker);

        // Which move do we think the attacker is using? (Champion guesses energy exactly; never below its cheapest move)
        int estimatedEnergy = attackerEnergy;
        int minimumEnergy = 100;
        for (BattleMove move : attacker.chargedMoves)
        {
            minimumEnergy = Math.min(minimumEnergy, move.energy);
        }
        estimatedEnergy = Math.max(estimatedEnergy, minimumEnergy);

        // PvPoke means to refresh each move's damage here but sets it on the array, so the cached values are used
        List<BattleMove> moves = new ArrayList<>();
        for (BattleMove move : attacker.chargedMoves)
        {
            if (estimatedEnergy >= move.energy)
            {
                moves.add(move);
            }
        }
        moves.sort((a, b) -> Integer.compare(b.damage, a.damage)); // stable, most damage first

        long[] guessWeights = new long[moves.size()];
        for (int i = 0; i < moves.size(); i++)
        {
            long weight = 1;
            if (i == 0 && attacker.hp / (double) attacker.maxHp <= .25)
            {
                weight += 8; // the opponent is low on HP: probably the higher damage move
                if (moves.get(i).moveId.equals("ACID_SPRAY"))
                {
                    weight += 12;
                }
            }
            if (i == 0)
            {
                weight += me.shields(); // more cautious with more shields
            }
            if (me.remaining() == 1 && moves.get(i).damage >= defender.hp)
            {
                weight += 4; // last Pokemon and this move faints me
            }
            if (i == 1 && moves.get(1).damage < moves.get(0).damage && moves.get(1).energy >= moves.get(0).energy
                && !moves.get(1).moveId.equals("ACID_SPRAY"))
            {
                guessWeights[0] += 20; // lower damage and higher energy: definitely the other one
            }
            guessWeights[i] = weight;
        }
        BattleMove move = moves.get(argmax(guessWeights)); // the guessed move

        int yesWeight = 4; // 4 + (3 - level) * 2 at Champion
        int noWeight = 4;

        // Will this attack hurt?
        int damageWeight = (int) Math.min(Math.round(move.damage / Math.max(defender.hp, defender.maxHp / 2.0) * 10), 10);
        int moveDamage = move.damage;
        int fastMoveDamage = attacker.fastMove.damage;

        if (damageWeight >= 5)
        {
            yesWeight += (damageWeight - 3) * (me.shields() + 1); // prefer shielding high damage moves
        }
        else
        {
            noWeight += (8 - damageWeight) * 2;
        }

        // Prefer to shield hard hitting or knockout moves in good matchups over bad matchups
        if (damageWeight >= 6 || moveDamage + fastMoveDamage * 2 >= defender.hp)
        {
            if (me.remaining() > 1)
            {
                double yesRating = currentRating - 400;
                double noRating = 400 - currentRating;
                if (defender.hp / (double) defender.maxHp < .35)
                {
                    yesRating = currentRating - 500;
                    noRating = 500 - currentRating;
                }
                if (me.switchTimer() > 0)
                {
                    yesRating *= 2; // locked in: shield good matchups, let bad ones go
                    noRating *= 2;
                }
                yesWeight += Math.round((yesRating / 100) * damageWeight);
                noWeight += Math.round((noRating / 100) * (10 - damageWeight));
            }
            else
            {
                yesWeight += damageWeight;
                noWeight -= damageWeight;
            }
        }

        // ADVANCED_SHIELDING: close to a Charged Move of my own that faints or seriously hurts the attacker?
        // (PvPoke tests the guessed move's damage here, not my move's; kept as is)
        for (BattleMove mine : defender.chargedMoves)
        {
            double turnsAway = Math.ceil((mine.energy - defender.energy) / (double) defender.fastMove.energyGain) * defender.fastMove.turns;
            if ((moveDamage >= attacker.hp || moveDamage >= defender.maxHp * .8) && turnsAway <= 1)
            {
                yesWeight += 4;
            }
        }

        // Preserve shield advantage where possible (PvPoke's extra "/ 4" compares an object with 500, so never runs)
        if (me.remaining() > 1 && attacker.startingShields >= defender.startingShields && defender.shieldsUsed > 0)
        {
            yesWeight = (int) Math.round(yesWeight / 2.0);
        }

        // Last Pokemon remaining: protect it at all costs
        if (me.remaining() == 1)
        {
            yesWeight *= 2;
            noWeight = (int) Math.round(noWeight / 4.0);
        }

        // BAD_DECISION_PROTECTION: a 4x favourite becomes the only option
        if (yesWeight / (double) noWeight >= 4)
        {
            noWeight = 0;
        }
        else if (noWeight / (double) yesWeight >= 4)
        {
            yesWeight = 0;
        }

        // chooseOption([yes, no]): highest weight, yes on ties (and when neither is positive)
        return noWeight > Math.max(yesWeight, 0) ? Shield.TAKE_IT : Shield.SHIELD;
    }

    /** Index of the largest weight, first on ties (all non-positive -> 0). */
    private static int argmax(long[] weights)
    {
        int best = 0;
        for (int i = 1; i < weights.length; i++)
        {
            if (weights[i] > weights[best])
            {
                best = i;
            }
        }
        return best;
    }

    // ---- decideSwitch: who comes in ----

    /** Who a voluntary switch-out brings in. */
    int switchTarget(TeamView me, TeamView them)
    {
        if (followsRoles && me.team().size() > SAFE_SWAP)
        {
            BattlePokemon safeSwap = me.team().get(SAFE_SWAP);
            if (safeSwap.hp > 0 && safeSwap != me.active())
            {
                return SAFE_SWAP;
            }
        }
        return decideSwitch(me, them);
    }

    @Override
    public int chooseReplacement(TeamView me, TeamView them)
    {
        return decideSwitch(me, them);
    }

    private static int decideSwitch(TeamView me, TeamView them)
    {
        BattlePokemon poke = me.active();
        BattlePokemon opponent = them.active();
        int best = -1;
        long bestWeight = -1;

        for (int i = 0; i < me.team().size(); i++)
        {
            BattlePokemon candidate = me.team().get(i);
            if (candidate.hp <= 0 || candidate == poke)
            {
                continue;
            }
            double average = scenario("NO_BAIT", candidate, me.shields(), opponent);
            long weight;
            if (average < 500)
            {
                weight = Math.round(Math.pow(average / 100, 4) / 20); // dramatically scale down a losing matchup
            }
            else if (them.switchTimer() > 10 || poke.hp <= 0)
            {
                weight = Math.round(Math.pow((average - 250) / 100, 4)); // opponent switch locked: the hard counter
            }
            else
            {
                weight = Math.round(Math.pow((1000 - average) / 100, 4)); // opponent can switch: the softer counter
            }
            weight = Math.max(weight, 1);

            if (weight > bestWeight)
            {
                best = i;
                bestWeight = weight;
            }
        }
        return best;
    }

    // ---- runScenario ----

    /** runScenario: 1v1s from the current state over every shield split, weighted 4/4/1 per side; the average rating. */
    /**
     * A scenario's inputs: which two Pokémon (by identity: Team Builder reuses the same Combatant and Moveset objects
     * across thousands of battles), the state a copy carries (HP, energy, cooldown, stat stages) and the shields.
     * The result depends on nothing else, so it can be remembered.
     */
    private record ScenarioKey(String type, Combatant self, Moveset selfMoves, long selfState, int shields,
                               Combatant other, Moveset otherMoves, long otherState, int otherShields)
    {
        @Override
        public boolean equals(Object o)
        {
            return o instanceof ScenarioKey k && type.equals(k.type) && self == k.self && selfMoves == k.selfMoves
                && selfState == k.selfState && shields == k.shields && other == k.other && otherMoves == k.otherMoves
                && otherState == k.otherState && otherShields == k.otherShields;
        }

        @Override
        public int hashCode()
        {
            int h = type.hashCode();
            h = 31 * h + System.identityHashCode(self);
            h = 31 * h + System.identityHashCode(selfMoves);
            h = 31 * h + Long.hashCode(selfState);
            h = 31 * h + shields;
            h = 31 * h + System.identityHashCode(other);
            h = 31 * h + System.identityHashCode(otherMoves);
            h = 31 * h + Long.hashCode(otherState);
            return 31 * h + otherShields;
        }

        /** HP, energy, cooldown and both stat stages packed into one number (each fits in its bits). */
        static long state(BattlePokemon p)
        {
            return ((long) p.hp << 40) | ((long) p.energy << 32) | ((long) (p.cooldown & 0xFFFF) << 16)
                | ((long) (p.statBuffs[0] + 128) << 8) | (p.statBuffs[1] + 128);
        }
    }

    /** Remembered scenario ratings; shared by every battle on every thread, emptied when it grows past the cap. */
    private static final Map<ScenarioKey, Double> SCENARIOS = new ConcurrentHashMap<>();
    static final int SCENARIO_CACHE_CAP = 500_000;

    static double scenario(String type, BattlePokemon pokemon, int shields, BattlePokemon opponent)
    {
        // NEITHER_BAIT and NO_BAIT set up the same battles (bait off on both sides); PvPoke runs both
        String setup = "NO_BAIT".equals(type) ? "NEITHER_BAIT" : type;
        ScenarioKey key = new ScenarioKey(setup, pokemon.combatant, pokemon.moveset, ScenarioKey.state(pokemon), shields,
            opponent.combatant, opponent.moveset, ScenarioKey.state(opponent), opponent.shields);
        Double known = SCENARIOS.get(key);
        if (known != null)
        {
            return known;
        }
        double rating = simulateScenario(type, pokemon, shields, opponent);
        if (SCENARIOS.size() >= SCENARIO_CACHE_CAP)
        {
            SCENARIOS.clear();
        }
        SCENARIOS.put(key, rating);
        return rating;
    }

    static double simulateScenario(String type, BattlePokemon pokemon, int shields, BattlePokemon opponent)
    {
        double total = 0;
        int totalWeight = 0;
        for (int i = 0; i <= shields; i++)
        {
            for (int n = 0; n <= opponent.shields; n++)
            {
                BattlePokemon self = pokemon.copy(0);
                BattlePokemon other = opponent.copy(1);
                self.shields = i;
                other.shields = n;
                switch (type)
                {
                    case "BOTH_BAIT" -> { self.baitShields = 1; other.baitShields = 1; }
                    case "NEITHER_BAIT", "NO_BAIT" -> { self.baitShields = 0; other.baitShields = 0; }
                    case "FARM" -> { self.baitShields = 1; self.farmEnergy = true; other.baitShields = 1; }
                    default -> throw new IllegalArgumentException(type);
                }

                new Battle.Engine(self, other).run();

                int weight = SHIELD_WEIGHTS[i] * SHIELD_WEIGHTS[n];
                total += Battle.rating(self, other) * weight;
                totalWeight += weight;
            }
        }
        return total / totalWeight;
    }
}

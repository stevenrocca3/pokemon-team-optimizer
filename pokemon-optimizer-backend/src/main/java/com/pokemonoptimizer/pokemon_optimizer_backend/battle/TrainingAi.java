package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import java.util.LinkedHashMap;
import java.util.Map;

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
                return decideSwitch(me, them);
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

    // ---- decideSwitch: who comes in ----

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
    static double scenario(String type, BattlePokemon pokemon, int shields, BattlePokemon opponent)
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

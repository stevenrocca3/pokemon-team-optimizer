package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Port of PvPoke's ActionLogic.decideAction and ActionLogic.wouldShield (src/js/battle/actions/ActionLogic.js,
 * commit e02bde7), kept close to the original so the battle results match PvPoke, quirks included.
 * Species form-change special cases (Mimikyu, Minior, Cramorant, Aegislash) are not ported.
 */
final class ActionLogic
{
    private ActionLogic() {}

    /** DamageCalculator.damage against the Pokémon's current stat stages. */
    static int damage(BattlePokemon attacker, BattlePokemon defender, BattleMove move)
    {
        return DamageCalculator.damage(move.power, attacker.effectiveAttack(), defender.effectiveDefense(),
            move.stabFor(attacker.types), move.effectivenessAgainst(defender.types));
    }

    /** A survival-check state: ELEMENTS OF STATE: POKEMON HP, OPPONENT ENERGY, CURRENT TURN, SHIELDS. */
    private record SurvivalState(int hp, int opEnergy, double turn, int shields) {}

    /** PvPoke's BattleState for the DP planner. Fields are doubles because they are JS numbers. */
    private static final class PlanState
    {
        final double energy;
        final double oppHealth;
        final double turn;
        final double oppShields;
        final List<BattleMove> moves;
        double buffs;
        final double chance;

        PlanState(double energy, double oppHealth, double turn, double oppShields, List<BattleMove> moves, double buffs, double chance)
        {
            this.energy = energy;
            this.oppHealth = oppHealth;
            this.turn = turn;
            this.oppShields = oppShields;
            this.moves = moves;
            this.buffs = buffs;
            this.chance = chance;
        }
    }

    private static List<BattleMove> plus(List<BattleMove> moves, BattleMove move)
    {
        List<BattleMove> copy = new ArrayList<>(moves);
        copy.add(move);
        return copy;
    }

    /** Returns the index into poke.chargedMoves to throw, or null to use a Fast Move. */
    static Integer decideAction(Battle.Engine battle, BattlePokemon poke, BattlePokemon opponent)
    {
        int turns = battle.turns;
        List<Double> chargedMoveReady = new ArrayList<>();
        boolean winsCMP = poke.statAtk >= opponent.statAtk;

        int fastDamage = damage(poke, opponent, poke.fastMove);
        int oppFastDamage = damage(opponent, poke, opponent.fastMove);
        boolean hasNonDebuff = false;

        List<BattleMove> active = poke.activeChargedMoves;
        BattleMove fast = poke.fastMove;
        BattleMove oppFast = opponent.fastMove;

        // If no Charged Moves at all, return
        if (active.size() < 1)
        {
            return null;
        }

        // If no charged move ready or farm energy is on, always throw fast move
        if (poke.energy < poke.fastestChargedMove.energy || poke.farmEnergy)
        {
            return null;
        }

        // Evaluate cooldown to reach each charge move
        for (BattleMove move : active)
        {
            if (!move.selfDebuffing)
            {
                hasNonDebuff = true;
            }
            if (poke.energy >= move.energy)
            {
                chargedMoveReady.add(0.0);
            }
            else
            {
                chargedMoveReady.add(Math.ceil((move.energy - poke.energy) / (double) fast.energyGain) * fast.turns);
            }
        }

        double turnsToLive = Double.POSITIVE_INFINITY;
        Deque<SurvivalState> queue = new ArrayDeque<>();

        // Check if opponent is in the middle of a fast move and adjust accordingly
        if (opponent.cooldown != 0)
        {
            queue.push(new SurvivalState(poke.hp - oppFastDamage, opponent.energy + oppFast.energyGain, opponent.cooldown / 500.0, poke.shields));
        }
        else
        {
            queue.push(new SurvivalState(poke.hp, opponent.energy, 0, poke.shields));
        }

        // Check if opponent can KO in your fast move cooldown
        while (!queue.isEmpty())
        {
            SurvivalState curr = queue.pop();

            // If turn > when you can act before your opponent, move to the next item in the queue
            if (curr.hp() > oppFastDamage)
            {
                if (winsCMP)
                {
                    if (curr.turn() > fast.turns)
                    {
                        continue;
                    }
                }
                else if (curr.turn() > fast.turns + 1)
                {
                    continue;
                }
            }

            // Shield bait if shields are up, otherwise try to KO
            if (curr.shields() != 0)
            {
                if (opponent.fastestChargedMove != null && curr.opEnergy() >= opponent.fastestChargedMove.energy)
                {
                    queue.push(new SurvivalState(curr.hp() - 1, curr.opEnergy() - opponent.fastestChargedMove.energy,
                        curr.turn() + 1, curr.shields() - 1));
                }
            }
            else
            {
                // Check if any charge move KO's, add results to queue
                for (BattleMove move : opponent.activeChargedMoves)
                {
                    if (curr.opEnergy() >= move.energy)
                    {
                        int moveDamage = damage(opponent, poke, move);

                        if (moveDamage >= curr.hp())
                        {
                            turnsToLive = Math.min(curr.turn(), turnsToLive);

                            if (poke.statAtk > opponent.statAtk && oppFast.cooldown % fast.cooldown == 0)
                            {
                                turnsToLive++;
                            }
                            break;
                        }
                        queue.push(new SurvivalState(curr.hp() - moveDamage, curr.opEnergy() - move.energy, curr.turn() + 1, curr.shields()));
                    }
                }
            }

            // Check if a fast move faints, add results to queue
            if (curr.hp() - oppFastDamage <= 0)
            {
                turnsToLive = Math.min(curr.turn() + oppFast.turns, turnsToLive);
                break;
            }
            else
            {
                queue.push(new SurvivalState(curr.hp() - oppFastDamage, curr.opEnergy() + oppFast.energyGain,
                    curr.turn() + oppFast.turns, curr.shields()));
            }
        }

        // If you can't throw a fast move and live, throw whatever move you can with the most damage
        if (poke.hp <= oppFast.damage * 2 && oppFast.cooldown == 500)
        {
            turnsToLive--;
        }

        // Anticipate a Fast Move landing that has already initiated
        if (poke.hp <= oppFast.damage && opponent.cooldown > 0 && oppFast.cooldown > 500)
        {
            turnsToLive = opponent.cooldown / 500.0;

            if (opponent.hp > fast.damage)
            {
                turnsToLive--;
            }
        }

        // Anticipate a Fast Move landing if you use your Fast Move
        if (poke.hp <= oppFast.damage && opponent.cooldown == 0 && oppFast.cooldown <= fast.cooldown + 500)
        {
            if (opponent.hp > fast.damage)
            {
                turnsToLive--;
            }
        }

        if (turnsToLive * 500 < fast.cooldown || (turnsToLive * 500 == fast.cooldown && !winsCMP)
            || (turnsToLive * 500 == fast.cooldown && poke.hp <= oppFast.damage))
        {
            int maxDamageMoveIndex = 0;
            int prevMoveDamage = -1;

            for (int n = active.size() - 1; n >= 0; n--)
            {
                // Find highest damage available move
                if (chargedMoveReady.get(n) == 0)
                {
                    int moveDamage = damage(poke, opponent, active.get(n));

                    // If this move deals more damage than the other move, use it
                    if (moveDamage > prevMoveDamage)
                    {
                        maxDamageMoveIndex = poke.chargedMoves.indexOf(active.get(n));
                        prevMoveDamage = moveDamage;
                    }

                    // If the Pokemon can fire two of this move and deal more damage, use it
                    if (poke.energy >= active.get(n).energy * 2 && poke.statAtk > opponent.statAtk && moveDamage * 2 > prevMoveDamage)
                    {
                        maxDamageMoveIndex = poke.chargedMoves.indexOf(active.get(n));
                        prevMoveDamage = moveDamage * 2;
                    }
                }
            }

            // If no moves available, throw fast move
            if (prevMoveDamage == -1)
            {
                return null;
            }
            return maxDamageMoveIndex;
        }

        // Throw a lethal Charged Move if it will faint the opponent
        if (!poke.farmEnergy && opponent.shields == 0)
        {
            for (int n = 0; n < active.size(); n++)
            {
                BattleMove move = active.get(n);
                int moveIndex = poke.chargedMoves.indexOf(move);

                if (poke.energy >= move.energy)
                {
                    int moveDamage = damage(poke, opponent, move);

                    // Don't throw self debuffing moves at this point, or if the opponent will faint from Fast Move damage
                    if (opponent.hp <= moveDamage && !move.selfDebuffing && (n == 0 || (n == 1 && poke.baitShields == 0))
                        && opponent.hp > fast.damage)
                    {
                        return moveIndex;
                    }
                }
            }
        }

        // Optimize move timing to reduce free turns
        if (poke.optimizeMoveTiming)
        {
            int targetCooldown = 500; // Look to throw moves when opponent is at this cooldown or lower

            if (fast.cooldown >= 2000)
            {
                targetCooldown = 1000;
            }
            if (fast.cooldown >= 1500 && oppFast.cooldown == 2500)
            {
                targetCooldown = 1000;
            }
            if (fast.cooldown == 1000 && oppFast.cooldown == 2000)
            {
                targetCooldown = 1000;
            }

            // Don't optimize timing for Pokemon with the same duration moves
            if (fast.cooldown == oppFast.cooldown)
            {
                targetCooldown = 0;
            }

            // Don't optimize timing for Pokemon with longer, even duration moves (ie 4 vs 2, 3 vs 1)
            if (fast.cooldown % oppFast.cooldown == 0 && fast.cooldown > oppFast.cooldown)
            {
                targetCooldown = 0;
            }

            // Perform additional checks to execute optimal timing
            if ((opponent.cooldown == 0 || opponent.cooldown > targetCooldown) && targetCooldown > 0)
            {
                boolean optimizeTiming = true;

                // Don't optimize if we're about to faint from a fast move
                if (poke.hp <= oppFast.damage)
                {
                    optimizeTiming = false;
                }

                // Don't optimize if we'll go over 100 energy
                int queuedFastMoves = battle.queuedFastMoves(poke.index) + 1; // Add 1 for the Fast Move we are thinking about doing

                if (poke.energy + (fast.energyGain * queuedFastMoves) > 100)
                {
                    optimizeTiming = false;
                }

                // Don't optimize if we have fewer turns to live than we can throw Charged Moves
                double turnsPlanned = fast.turns + Math.floor(poke.energy / (double) active.get(0).energy);

                if (poke.statAtk < opponent.statAtk)
                {
                    turnsPlanned++;
                }

                if (turnsPlanned > turnsToLive)
                {
                    optimizeTiming = false;
                }

                // Don't optimize if we can KO with a Charged Move
                if (opponent.shields == 0)
                {
                    for (BattleMove move : active)
                    {
                        move.damage = damage(poke, opponent, move);

                        if (poke.energy >= move.energy && move.damage >= opponent.hp)
                        {
                            optimizeTiming = false;
                            break;
                        }
                    }
                }

                // Don't optimize if our opponent can KO with a Charged Move
                for (BattleMove move : opponent.activeChargedMoves)
                {
                    double fastMovesFromCharged = Math.ceil((move.energy - opponent.energy) / (double) oppFast.energyGain);
                    double fastMovesInFastMove = Math.floor(fast.cooldown / (double) oppFast.cooldown); // How many Fast Moves can the opponent get in if we do an extra move?
                    double turnsFromMove = (fastMovesFromCharged * oppFast.turns) + 1;

                    move.damage = damage(opponent, poke, move);

                    double moveDamage = move.damage + (oppFast.damage * fastMovesInFastMove);

                    if (poke.shields > 0)
                    {
                        moveDamage = 1 + (oppFast.damage * fastMovesInFastMove);
                    }

                    if (turnsFromMove <= fast.turns && moveDamage >= poke.hp)
                    {
                        optimizeTiming = false;
                        break;
                    }
                }

                // Don't optimize if the opponent will KO with Fast Moves it can fit into our Fast Move
                double fastMovesInFastMove = Math.floor((fast.cooldown + 500) / (double) oppFast.cooldown);
                if (poke.hp <= oppFast.damage * fastMovesInFastMove)
                {
                    optimizeTiming = false;
                }

                if (optimizeTiming)
                {
                    return null;
                }
            }
        }

        // Evaluate if opponent can't be fainted in a limited number of cycles. If so, do a simpler move selection.
        BattleMove best = poke.bestChargedMove;
        int bestChargedDamage = damage(poke, opponent, best);
        double bestCycleDamage = bestChargedDamage + (fastDamage * Math.ceil(best.energy / (double) fast.energyGain));
        double minimumCycleThreshold = 2;

        // Prefer non-debuffing moves when it will take multiple to KO
        if (best.selfDebuffing && best.energy > poke.fastestChargedMove.energy && best.dpe / poke.fastestChargedMove.dpe < 2)
        {
            minimumCycleThreshold = 1.1;
        }

        if (opponent.hp / bestCycleDamage > minimumCycleThreshold)
        {
            // It's going to take a lot of cycles to KO, so just throw the best move
            BattleMove selectedMove = best;

            if (active.size() > 1)
            {
                for (BattleMove move : active)
                {
                    if (best.selfDebuffing && !move.selfDebuffing && (selectedMove.dpe / move.dpe < 2))
                    {
                        selectedMove = move;
                    }

                    if (poke.baitShields != 0 && opponent.shields > 0 && !active.get(0).selfDebuffing
                        && wouldShield(battle, poke, opponent, move))
                    {
                        selectedMove = active.get(0);
                    }
                }
            }

            if (poke.energy < selectedMove.energy)
            {
                return null;
            }

            // Stack self debuffing moves
            if (selectedMove.selfDebuffing)
            {
                double energyToReach = poke.energy + (Math.floor((100 - poke.energy) / (double) fast.energyGain) * fast.energyGain);
                if (poke.energy < energyToReach)
                {
                    return null;
                }
            }
            return poke.chargedMoves.indexOf(selectedMove);
        }

        // Calculate the most efficient way to defeat opponent
        PlanState finalState = plan(battle, poke, opponent, hasNonDebuff, turns);
        if (finalState == null)
        {
            return null;
        }
        return chooseFromPlan(battle, poke, opponent, finalState);
    }

    /** The DP planner. Returns the chosen final state, or null for a Fast Move. Sets poke.turnsToKO. */
    private static PlanState plan(Battle.Engine battle, BattlePokemon poke, BattlePokemon opponent, boolean hasNonDebuff, int turns)
    {
        List<BattleMove> active = poke.activeChargedMoves;
        BattleMove fast = poke.fastMove;

        int stateCount = 0;
        List<PlanState> dpQueue = new ArrayList<>();
        dpQueue.add(new PlanState(poke.energy, opponent.hp, 0, opponent.shields, new ArrayList<>(), 0, 1));
        List<PlanState> stateList = new ArrayList<>();

        while (!dpQueue.isEmpty())
        {
            // A not very good way to prevent infinite loops
            if (stateCount >= 500)
            {
                return null;
            }
            stateCount++;

            PlanState curr = dpQueue.remove(0);
            List<Double> dpChargedMoveReady = new ArrayList<>();

            // Set cap of 4 for buffs
            curr.buffs = Math.max(-4, Math.min(4, curr.buffs));

            // Found fastest way to defeat enemy
            if (curr.oppHealth <= 0)
            {
                stateList.add(curr);

                if (curr.chance == 1)
                {
                    break;
                }
                continue;
            }

            // Evaluate cooldown to reach each charge move
            for (BattleMove move : active)
            {
                if (curr.energy >= move.energy)
                {
                    dpChargedMoveReady.add(0.0);
                }
                else
                {
                    dpChargedMoveReady.add(Math.ceil((move.energy - curr.energy) / fast.energyGain) * fast.turns);
                }
            }

            // Push states onto queue in order of TURN
            for (int n = 0; n < active.size(); n++)
            {
                BattleMove move = active.get(n);

                // Apply stat changes to pokemon attack
                int[] currentStatBuffs = poke.copyStatBuffs();
                poke.applyStatBuffs((int) curr.buffs, 0);

                int moveDamage = damage(poke, opponent, move);
                int fastSimulatedDamage = damage(poke, opponent, fast);

                // Remove stat changes from pokemon attack
                poke.setStatBuffs(currentStatBuffs);

                // Skip self defense debuffing moves like Superpower if they aren't lethal
                if (hasNonDebuff && poke.speciesName.equals("Melmetal") && opponent.speciesName.equals("Cresselia"))
                {
                    if (move.selfDebuffing && move.buffs[1] < 1
                        && opponent.hp > moveDamage * (1 + 4.0 / (4 - move.buffs[0])))
                    {
                        continue;
                    }
                }

                // Add result of farming down from this point
                double movesToFarmDown = Math.ceil(curr.oppHealth / fastSimulatedDamage);
                double farmTurn = curr.turn + movesToFarmDown * fast.turns;
                // PvPoke passes currState.opponentShields here, which is undefined; the value is never read again.
                PlanState farmDown = new PlanState(curr.energy + fast.energyGain * movesToFarmDown, 0, farmTurn,
                    Double.NaN, curr.moves, curr.buffs, curr.chance);
                insertAfterTurn(dpQueue, farmDown);

                // Find new attack after move
                double attackMult = curr.buffs;

                // If attack changes attack stat, apply effects (chance-based branches are disabled in PvPoke)
                if (move.buffApplyChance != 0 && "self".equals(move.buffTarget) && move.buffApplyChance == 1)
                {
                    attackMult += move.buffs[0];
                }
                if (move.buffApplyChance != 0 && "opponent".equals(move.buffTarget) && move.buffApplyChance == 1)
                {
                    attackMult -= move.buffs[1];
                }

                double newShields = curr.oppShields;
                if (newShields > 0)
                {
                    newShields--;
                }

                // If move is ready, use it and add results to queue
                if (dpChargedMoveReady.get(n) == 0)
                {
                    // If shielded, apply 1 damage, otherwise apply move damage
                    double newOppHealth = curr.oppHealth - moveDamage;
                    if (curr.oppShields > 0)
                    {
                        newOppHealth = curr.oppHealth - 1;
                    }

                    // Remove all elements that are strictly worse than this state while checking if there are any elements better than this state
                    int i = 0;
                    boolean insertElement = true;
                    while (i < dpQueue.size() && dpQueue.get(i).turn == curr.turn + 1)
                    {
                        PlanState other = dpQueue.get(i);
                        if (other.oppHealth == newOppHealth && other.buffs == attackMult)
                        {
                            if (other.energy == (curr.energy - move.energy))
                            {
                                // If energy is the same and opponent at same health choose path with less debuffs or more buff chances
                                if (debuffScore(other.moves) > debuffScore(plus(curr.moves, move)))
                                {
                                    dpQueue.remove(i);
                                }
                                else
                                {
                                    insertElement = false;
                                    i++;
                                }
                            }
                            else
                            {
                                insertElement = false;
                                i++;
                            }
                        }
                        else
                        {
                            i++;
                        }
                    }

                    if (insertElement)
                    {
                        double nextTurn = curr.turn + 1;
                        insertAfterTurn(dpQueue, new PlanState(curr.energy - move.energy, newOppHealth, nextTurn, newShields,
                            plus(curr.moves, move), attackMult, curr.chance));
                    }

                    // If move will debuff attack, calculate values when you stack two of them then throw
                    if (move.selfDebuffing && move.buffs[0] < 0 && move.energy * 2 <= 100)
                    {
                        double newTurn = Math.ceil((move.energy * 2 - curr.energy) / fast.energyGain) * fast.turns;
                        double newEnergy = Math.floor(newTurn / fast.turns) * fast.energyGain + curr.energy - move.energy;

                        if (newTurn != 0)
                        {
                            double stackedHealth = curr.oppHealth - fastSimulatedDamage * (newTurn / fast.turns);
                            stackedHealth = curr.oppShields > 0 ? stackedHealth - 1 : stackedHealth - moveDamage;

                            double stackedTurn = newTurn + curr.turn + 1;
                            insertAfterTurn(dpQueue, new PlanState(newEnergy, stackedHealth, stackedTurn, newShields,
                                plus(curr.moves, move), attackMult, curr.chance));
                        }
                    }
                }
                else
                {
                    double ready = dpChargedMoveReady.get(n);
                    double newEnergy = curr.energy - move.energy + fast.energyGain * (ready / fast.turns);
                    double newOppHealth = curr.oppHealth - moveDamage - fastSimulatedDamage * (ready / fast.turns);

                    // If shields are up, only apply fast move damage
                    if (curr.oppShields > 0)
                    {
                        newOppHealth = curr.oppHealth - fastSimulatedDamage * (ready / fast.turns) - 1;
                    }
                    double newTurn = curr.turn + ready + 1;

                    insertBeforeTurn(dpQueue, new PlanState(newEnergy, newOppHealth, newTurn, newShields,
                        plus(curr.moves, move), attackMult, curr.chance));

                    // If move will debuff attack, calculate values when you stack two of them then throw
                    if (move.selfDebuffing && move.buffs[0] < 0 && move.energy * 2 <= 100)
                    {
                        double stackTurns = Math.ceil((move.energy * 2 - curr.energy) / fast.energyGain) * fast.turns;
                        double stackEnergy = Math.floor(stackTurns / fast.turns) * fast.energyGain + curr.energy - move.energy;

                        double stackedHealth = curr.oppHealth - fastSimulatedDamage * (stackTurns / fast.turns);
                        stackedHealth = curr.oppShields > 0 ? stackedHealth - 1 : stackedHealth - moveDamage;

                        double stackedTurn = stackTurns + curr.turn + 1;
                        insertBeforeTurn(dpQueue, new PlanState(stackEnergy, stackedHealth, stackedTurn, newShields,
                            plus(curr.moves, move), attackMult, curr.chance));
                    }
                }
            }
        }

        // Set our turnsToKO to our guaranteed KO turn
        if (stateList.isEmpty())
        {
            return null;
        }
        poke.turnsToKO = turns + stateList.get(stateList.size() - 1).turn;

        // If opponent KOs before our guaranteed KO, PvPoke compares chance to a state object here,
        // which is never true, so the "best plan" is always the first state found.
        if (stateList.size() == 1)
        {
            return stateList.get(0);
        }
        if (opponent.turnsToKO != -1 && poke.turnsToKO > opponent.turnsToKO)
        {
            return stateList.get(0);
        }
        return stateList.get(stateList.size() - 1);
    }

    /**
     * Insert at the first index where keepGoing fails, scanning from the front (PvPoke's priority queue insert).
     * PvPoke's "is there a better state" checks inside these loops read fields that don't exist, so they never fire.
     */
    // PvPoke inserts into dpQueue by scanning from the front while a turn test holds. Every insert uses the new
    // state's own turn, so the queue stays sorted by turn and the scan's stopping point is a binary search bound.

    /** Where PvPoke scans `while (queue[i].turn <= state.turn)`: after every state on the same turn. */
    private static void insertAfterTurn(List<PlanState> queue, PlanState state)
    {
        queue.add(searchTurn(queue, state.turn, true), state);
    }

    /** Where PvPoke scans `while (queue[i].turn < state.turn)`: before every state on the same turn. */
    private static void insertBeforeTurn(List<PlanState> queue, PlanState state)
    {
        queue.add(searchTurn(queue, state.turn, false), state);
    }

    private static int searchTurn(List<PlanState> queue, double turn, boolean afterEqual)
    {
        int low = 0;
        int high = queue.size();
        while (low < high)
        {
            int mid = (low + high) >>> 1;
            double t = queue.get(mid).turn;
            if (t < turn || (afterEqual && t == turn))
            {
                low = mid + 1;
            }
            else
            {
                high = mid;
            }
        }
        return low;
    }

    private static int debuffScore(List<BattleMove> moves)
    {
        int score = 0;
        for (BattleMove move : moves)
        {
            if (move.selfDebuffing)
            {
                score++;
            }
            if (move.buffApplyChance == 1 && "self".equals(move.buffTarget) && move.buffs[0] + move.buffs[1] > 0)
            {
                score--;
            }
        }
        return score;
    }

    /** Evaluate throwing strategy after finding the optimal plan. */
    private static Integer chooseFromPlan(Battle.Engine battle, BattlePokemon poke, BattlePokemon opponent, PlanState finalState)
    {
        List<BattleMove> active = poke.activeChargedMoves;
        List<BattleMove> moves = finalState.moves;

        // Return if plan is the farm down
        if (moves.isEmpty())
        {
            BattleMove boost = poke.boostMove();
            if (boost == null)
            {
                return null;
            }
            moves.add(boost);
        }

        // Find if there are any debuffing moves in planned move list
        boolean debuffingMove = false;
        for (BattleMove move : moves)
        {
            if (move.selfDebuffing)
            {
                debuffingMove = true;
            }
        }

        boolean baits = poke.baitShields != 0;

        // If bait shields, build up to most expensive charge move in planned move list
        if (baits && opponent.shields > 0 && active.size() > 1)
        {
            for (int i = 1; i < active.size(); i++)
            {
                if (poke.energy < active.get(i).energy && active.get(i).dpe > moves.get(0).dpe)
                {
                    boolean bait = true;

                    // Don't go for baits if you have an effective self buffing move
                    if (active.get(i).dpe / active.get(0).dpe <= 1.5 && active.get(0).selfBuffing)
                    {
                        bait = false;
                    }

                    if (bait)
                    {
                        return null;
                    }
                }
            }
        }

        // Don't bait if the opponent won't shield
        if (baits && opponent.shields > 0 && active.size() > 1)
        {
            for (int i = 1; i < active.size(); i++)
            {
                double dpeRatio = (active.get(i).damage / (double) active.get(i).energy)
                    / (moves.get(0).damage / (double) moves.get(0).energy);

                if (poke.energy >= active.get(i).energy && dpeRatio > 1.5)
                {
                    if (!wouldShield(battle, poke, opponent, active.get(i)))
                    {
                        moves.set(0, active.get(i));
                    }
                }
            }
        }

        // If not baiting shields or shields are down and no moves debuff, throw most damaging move first
        if (!baits || (opponent.shields == 0 && !debuffingMove))
        {
            moves.sort((a, b) -> damage(poke, opponent, b) - damage(poke, opponent, a));
        }

        // If shields are up, prefer low energy moves that are more efficient
        if (opponent.shields > 0 && active.size() > 1 && active.get(0).energy <= moves.get(0).energy
            && active.get(0).dpe > moves.get(0).dpe && !active.get(0).selfDebuffing)
        {
            moves.set(0, active.get(0));
        }

        // If shields are down, prefer non-debuffing moves if both sides have significant HP remaining
        if (opponent.shields == 0 && active.size() > 1 && moves.get(0).selfDebuffing && moves.get(0).energy > 50
            && (poke.hp / (double) poke.maxHp) > .5 && (moves.get(0).damage / (double) opponent.hp) < .8)
        {
            moves.set(0, active.get(0));
        }

        // Bandaid to force more efficient move of the same energy
        if (active.size() > 1 && active.get(0).energy == moves.get(0).energy && active.get(0).dpe > moves.get(0).dpe
            && !active.get(0).selfDebuffing)
        {
            moves.set(0, active.get(0));
        }

        // Bandaid to force more efficient move of the similar energy if chosen move is self debuffing
        if (active.size() > 1 && active.get(0).energy - 10 <= moves.get(0).energy && active.get(0).dpe > moves.get(0).dpe
            && moves.get(0).selfDebuffing && !active.get(0).selfDebuffing)
        {
            moves.set(0, active.get(0));
        }

        // Bandaid to force more efficient move of the similar energy if one move is self buffing
        if (active.size() > 1 && active.get(0).energy - moves.get(0).energy <= 5 && active.get(0).dpe > moves.get(0).dpe
            && active.get(0).selfBuffing)
        {
            moves.set(0, active.get(0));
        }

        // Don't bait with self debuffing moves
        if (baits && opponent.shields > 0 && active.size() > 1)
        {
            for (int i = 1; i < active.size(); i++)
            {
                if (poke.energy >= active.get(i).energy && active.get(i).dpe > moves.get(0).dpe)
                {
                    if (moves.get(0).selfDebuffing && !active.get(i).selfDebuffing)
                    {
                        moves.set(0, active.get(i));
                    }
                }
            }
        }

        // When shields are down, don't use self debuffing moves that are significantly less efficient
        if (opponent.shields == 0 && active.size() > 1 && moves.get(0).selfDebuffing)
        {
            for (int i = 1; i < active.size(); i++)
            {
                if (active.get(i).dpe > moves.get(0).dpe && !active.get(i).selfDebuffing)
                {
                    moves.set(0, active.get(i));
                }
            }
        }

        // While shields are up, prefer close non debuffing moves in scenarios where debuffing move won't KO
        if (opponent.shields > 0 && active.size() > 1)
        {
            for (int i = 1; i < active.size(); i++)
            {
                if (active.get(0).selfDebuffing && !active.get(i).selfDebuffing)
                {
                    if (baits || (opponent.hp - active.get(0).damage > 10))
                    {
                        if (active.get(i).energy - active.get(0).energy <= 10 && active.get(i).dpe / active.get(0).dpe > 0.7)
                        {
                            moves.set(0, active.get(i));
                        }
                    }
                }
            }
        }

        // Defer self debuffing moves until after survivable Charged Moves
        if (moves.get(0).selfDebuffing && poke.shields == 0 && poke.energy < 100 && opponent.bestChargedMove != null)
        {
            if (opponent.energy >= opponent.bestChargedMove.energy
                && !wouldShield(battle, opponent, poke, opponent.bestChargedMove) && !active.get(0).selfBuffing)
            {
                return null;
            }
        }

        // If move is self debuffing and doesn't KO, try to stack as much as you can
        if (moves.get(0).selfDebuffing)
        {
            double targetEnergy = Math.floor(100 / (double) moves.get(0).energy) * moves.get(0).energy;

            if (poke.energy < targetEnergy)
            {
                int moveDamage = damage(poke, opponent, moves.get(0));
                if ((opponent.hp > moveDamage || opponent.shields != 0)
                    && (poke.hp > opponent.fastMove.damage * 2 || opponent.fastMove.cooldown - poke.fastMove.cooldown > 500))
                {
                    return null;
                }
            }
            else if (baits && opponent.shields > 0 && active.get(0).energy - moves.get(0).energy <= 10 && !active.get(0).selfDebuffing)
            {
                // Use the lower energy move if it's a boosting move or if the opponent would shield the bigger move
                if (active.get(0).selfBuffing || wouldShield(battle, poke, opponent, moves.get(0)))
                {
                    moves.set(0, active.get(0));
                }
            }
        }

        // Use the final move, or a Fast Move if not enough energy
        if (poke.energy >= moves.get(0).energy)
        {
            return poke.chargedMoves.indexOf(moves.get(0));
        }
        return null;
    }

    /** Port of ActionLogic.wouldShield: would the defender shield this move? Mutates move.damage like PvPoke. */
    static boolean wouldShield(Battle.Engine battle, BattlePokemon attacker, BattlePokemon defender, BattleMove move)
    {
        boolean useShield = false;
        int damage = damage(attacker, defender, move);
        move.damage = damage;
        int postMoveHP = defender.hp - damage; // How much HP will be left after the attack

        // Capture current buffs for pokemon whose buffs will change
        int[] moveBuffs = move.hasBuffs() ? move.buffs : new int[] { 0, 0 };
        BattlePokemon buffed = moveBuffs[0] > 0 ? attacker : defender;
        int[] currentBuffs = buffed.copyStatBuffs();
        buffed.applyStatBuffs(moveBuffs[0], moveBuffs[1]);

        int fastDamage = damage(attacker, defender, attacker.fastMove);

        // Determine how much damage will be dealt per cycle to see if the defender will survive to shield the next cycle
        double fastAttacks = Math.ceil((move.energy - Math.max(attacker.energy - move.energy, 0)) / (double) attacker.fastMove.energyGain) + 1;
        double fastAttackDamage = fastAttacks * fastDamage;
        double cycleDamage = (fastAttackDamage + 1) * defender.shields;

        if (postMoveHP <= cycleDamage)
        {
            useShield = true;
        }

        // Reset buffs to original
        buffed.setStatBuffs(currentBuffs);

        // If the defender can't afford to let a charged move connect, block
        double fastDPT = fastDamage / (double) attacker.fastMove.turns;

        for (BattleMove chargedMove : attacker.chargedMoves)
        {
            if (attacker.energy + chargedMove.energy >= chargedMove.energy)
            {
                int chargedDamage = damage(attacker, defender, chargedMove);

                if (chargedDamage >= defender.hp / 1.4 && fastDPT > 1.5)
                {
                    useShield = true;
                }
                if (chargedDamage >= defender.hp - cycleDamage)
                {
                    useShield = true;
                }
            }
        }

        // Shield the first in a series of Attack debuffing moves like Superpower, if they would do major damage
        if (move.selfAttackDebuffing && (move.damage / (double) defender.hp > 0.55))
        {
            useShield = true;
        }

        // When a Pokemon is set to always bait, always return true for this value
        if (attacker.baitShields == 2)
        {
            useShield = true;
        }

        return useShield;
    }
}

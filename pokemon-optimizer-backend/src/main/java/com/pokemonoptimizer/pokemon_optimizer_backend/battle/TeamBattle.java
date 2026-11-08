package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import java.util.ArrayList;
import java.util.List;

public final class TeamBattle
{
    private TeamBattle() {}   // static entry point only, like Battle.simulate

    public static TeamBattleResult simulate(List<Combatant> teamA, List<Moveset> movesetsA,
                                            List<Combatant> teamB, List<Moveset> movesetsB)
    {
        return simulate(teamA, movesetsA, new TrainingAi(), teamB, movesetsB, new TrainingAi());
    }

    private static List<BattlePokemon> buildSide(List<Combatant> team, List<Moveset> movesets, int index)
    {
        List<BattlePokemon> side = new ArrayList<>();
        
        for (int i = 0; i < team.size(); i++)
        {
            side.add(new BattlePokemon(team.get(i), movesets.get(i), i == 0 ? 2 : 0, index));
        }
        return side;
    }

    private static List<Integer> hps(List<BattlePokemon> side)
    {
        List<Integer> hps = new ArrayList<>();
        for (BattlePokemon poke : side)
        {
            hps.add(poke.hp);
        }
        return hps;
    }

    static TeamBattleResult simulate(List<Combatant> teamA, List<Moveset> movesetsA, TeamPolicy policyA,
                                     List<Combatant> teamB, List<Moveset> movesetsB, TeamPolicy policyB)
    {
        List<BattlePokemon> sideA = buildSide(teamA, movesetsA, 0);
        List<BattlePokemon> sideB = buildSide(teamB, movesetsB, 1);
        List<List<BattlePokemon>> teams = List.of(sideA, sideB);
        TeamPolicy[] policies = { policyA, policyB};
        int[] active = { 0, 0 };  // list position of each side's active Pokémon (replaces activeA/activeB)
        int[] switchTimer = { 0, 0 }; // ms until that side can switch again
        class Sides
        {
            TeamView view(int side)
            {
                return new TeamView(teams.get(side), teams.get(side).get(active[side]), switchTimer[side]);
            }

            void evaluate(int side)
            {
                policies[side].evaluateMatchup(view(side), view(1 - side));
            }
        }
        Sides sides = new Sides();
        Battle.Switching switching = new Battle.Switching()
        {
            public int decideSwitch(int side, int turn, BattlePokemon poke, BattlePokemon opponent)
            {
                return policies[side].chooseAction(turn, sides.view(side), sides.view(1 - side));
            }

            public void matchupChanged()
            {
                sides.evaluate(0);
                sides.evaluate(1);
            }

            public boolean canSwitch(int side)
            {
                return switchTimer[side] == 0;
            }

            public void tick(int deltaTime)
            {
                // count both timers down by deltaTime, never below 0
                for (int side = 0; side < 2; side++)
                {
                    boolean runsOut = switchTimer[side] > 0 && switchTimer[side] <= deltaTime;
                    switchTimer[side] = Math.max(0, switchTimer[side] - deltaTime);
                    if (runsOut)
                    {
                        sides.evaluate(side);
                    }
                }
            }

            public BattlePokemon switchIn(int side, int teamIndex)
            {
                BattlePokemon outgoing = teams.get(side).get(active[side]); 
                BattlePokemon incoming = teams.get(side).get(teamIndex);
                switchTimer[side] = 45000;
                outgoing.setStatBuffs(new int[] { 0, 0 });
                incoming.shields = outgoing.shields;
                incoming.startingShields = outgoing.shields;
                active[side] = teamIndex;
                return incoming;
            }

            public TeamPolicy.Shield decideShield(BattlePokemon attacker, BattlePokemon defender, BattleMove move)
            {
                int side = defender.index;
                return policies[side].decideShield(sides.view(side), sides.view(1 - side), move, attacker.energy);
            }

            public int chooseReplacement(int side)
            {
                return policies[side].chooseReplacement(sides.view(side), sides.view(1 - side));   // stub: engine stops at the first faint, your loop handles it like before
            }

            public BattlePokemon replaceFainted(int side, int teamIndex)
            {
                BattlePokemon fainted = teams.get(side).get(active[side]); 
                BattlePokemon incoming = teams.get(side).get(teamIndex);
                incoming.shields = fainted.shields;
                incoming.startingShields = fainted.shields;
                active[side] = teamIndex;
                return incoming;
            }
        };

        Battle.Engine engine = new Battle.Engine(sideA.get(0), sideB.get(0), switching);
        switching.matchupChanged();
        engine.run();
        return new TeamBattleResult(hps(sideA), hps(sideB));
    }
}

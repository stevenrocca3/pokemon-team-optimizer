package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

public class TeamBattleTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();

    private static final Combatant MEDICHAM  = new Combatant(SPECIES.findById("medicham").orElseThrow(), 4, 14, 9, 20.5, false);
    private static final Combatant REGISTEEL = new Combatant(SPECIES.findById("registeel").orElseThrow(), 10, 10, 10, 25, false);
    private static final Move COUNTER = MOVES.findById("COUNTER").orElseThrow();   // 2 turns, 4 dmg to Registeel
    private static final Move LOCK_ON = MOVES.findById("LOCK_ON").orElseThrow();   // 1 turn, 1 dmg to Medicham

    private static final TeamPolicy NEVER_SWITCH = new SwitchOnTurn(-1, -1);

    private record ReplacementsOnly(TeamPolicy ai) implements TeamPolicy
    {
        public int chooseAction(int now, TeamView me, TeamView opponent)
        {
            return FIGHT;
        }

        public int chooseReplacement(TeamView me, TeamView opponent)
        {
            return ai.chooseReplacement(me, opponent);
        }
    }

    @Test
    public void damageCarriesIntoTheNextFight()
    {   // Fight 1 is BattleTest's: Medicham KOs Registeel #1 on turn 68 with 31/99 HP left.
        // Fight 2: Lock-On KOs the 31 HP Medicham on turn 31; Counter lands 15 times (60 dmg) -> 133 - 60 = 73.
        // If each fight started fresh, Medicham (99 HP) would KO Registeel #2 too: hpB [0, 0], A wins.
        TeamBattleResult result = TeamBattle.simulate(
            List.of(MEDICHAM), List.of(new Moveset(COUNTER, List.of())), NEVER_SWITCH,
            List.of(REGISTEEL, REGISTEEL), List.of(new Moveset(LOCK_ON, List.of()), new Moveset(LOCK_ON, List.of())), NEVER_SWITCH);
        assertEquals(List.of(0), result.hpA());
        assertEquals(List.of(0, 73), result.hpB());
        assertEquals(Winner.B, result.winner());
    }

    private static final Move DYNAMIC_PUNCH = MOVES.findById("DYNAMIC_PUNCH").orElseThrow(); // 45 energy, 44 dmg to Registeel

    @Test
    public void aTeamsTwoShieldsAreSharedAcrossItsPokemon()
    {   // Fight 1: Registeel #1 shields the first two Dynamic Punches (BattleTest.eachShieldIsUsedOnce) -> Medicham 51 HP, 3 energy.
        // Fight 2: B has no shields left, so Registeel #2 eats every Dynamic Punch -> Medicham wins with 20 HP.
        // Each Pokémon with its own 2 shields: Registeel #2 shields too -> 5 HP. No shields at all (today's code): 37 HP.
        TeamBattleResult result = TeamBattle.simulate(
            List.of(MEDICHAM), List.of(new Moveset(COUNTER, List.of(DYNAMIC_PUNCH))), NEVER_SWITCH,
            List.of(REGISTEEL, REGISTEEL), List.of(new Moveset(LOCK_ON, List.of()), new Moveset(LOCK_ON, List.of())), NEVER_SWITCH);
        assertEquals(List.of(20), result.hpA());
        assertEquals(List.of(0, 0), result.hpB());
        assertEquals(Winner.A, result.winner());
    }

    private static final Combatant ALTARIA = new Combatant(SPECIES.findById("altaria").orElseThrow(), 4, 12, 13, 28.5, false);
    private static final Move DRAGON_BREATH = MOVES.findById("DRAGON_BREATH").orElseThrow();
    private static final Move SKY_ATTACK    = MOVES.findById("SKY_ATTACK").orElseThrow();

    @Test
    public void afterAFaintTheBestCounterComesInNotTheNextInLine()
    {   // Registeel #1 faints to Medicham (51 HP, 3 energy, 2 shields left). B picks between:
        //   Registeel #2: NO_BAIT average 398    Altaria (resists Fighting, Sky Attack is SE): 903.8
        // Altaria comes in and wins with 100 HP; Registeel #2 never battles.
        // Next in line instead: Registeel #2 faints, Medicham at 20 HP, then Altaria wins with 132 -> hpB [0, 0, 132].
        TeamBattleResult result = TeamBattle.simulate(
            List.of(MEDICHAM), List.of(new Moveset(COUNTER, List.of(DYNAMIC_PUNCH))), NEVER_SWITCH,
            List.of(REGISTEEL, REGISTEEL, ALTARIA),
            List.of(new Moveset(LOCK_ON, List.of()), new Moveset(LOCK_ON, List.of()), new Moveset(DRAGON_BREATH, List.of(SKY_ATTACK))),
            new ReplacementsOnly(new TrainingAi()));
        assertEquals(List.of(0), result.hpA());
        assertEquals(List.of(0, 133, 100), result.hpB());
    }

     /** Switches to `target` on `turn`; after a faint, brings in the first Pokémon still alive. */
    private record SwitchOnTurn(int turn, int target) implements TeamPolicy
    {
        public int chooseAction(int now, TeamView me, TeamView opponent)
        {
            return now == turn ? target : FIGHT;
        }

        public int chooseReplacement(TeamView me, TeamView opponent)
        {
            for (int i = 0; i < me.team().size(); i++)
            {
                if (me.team().get(i).hp > 0)
                {
                    return i;
                }
            }
            return -1;
        }
    }

    @Test
    public void aSwitchedOutPokemonKeepsItsHpAndTheNewOneFightsOn()
    {   // B switches Registeel out on turn 1, before Medicham's first Counter lands (turn 2).
        // Registeel leaves untouched (133); Altaria resists Counter and beats Medicham.
        // Without the switch, Registeel fights Medicham and faints (0), as in damageCarriesIntoTheNextFight.
        TeamBattleResult result = TeamBattle.simulate(
            List.of(MEDICHAM), List.of(new Moveset(COUNTER, List.of())), NEVER_SWITCH,
            List.of(REGISTEEL, ALTARIA), List.of(new Moveset(LOCK_ON, List.of()), new Moveset(DRAGON_BREATH, List.of(SKY_ATTACK))),
            new SwitchOnTurn(1, 1));
        assertEquals(133, result.hpB().get(0));
        assertEquals(List.of(0), result.hpA());
        assertEquals(Winner.B, result.winner());
    }

     private static final Move POWER_UP_PUNCH = MOVES.findById("POWER_UP_PUNCH").orElseThrow(); // 35 energy, +1 Atk self
    private static final Combatant LEVEL_1_MEDICHAM = new Combatant(SPECIES.findById("medicham").orElseThrow(), 0, 0, 0, 1, false); // 9 HP

    @Test
    public void statStagesResetWhenAPokemonSwitchesOut()
    {   // Medicham reaches +2 Attack (two Power-Up Punches), then switches to a 9 HP Medicham on turn 29, which faints.
        // Medicham comes back at 73 HP / 2 energy vs Registeel (67 HP, 1 shield) at +0 and wins with 48 HP.
        // If it kept its +2, Counter hits harder and it wins with 50. Both are PvPoke oracle values.
        TeamBattleResult result = TeamBattle.simulate(
            List.of(MEDICHAM, LEVEL_1_MEDICHAM), List.of(new Moveset(COUNTER, List.of(POWER_UP_PUNCH)), new Moveset(COUNTER, List.of())),
            new SwitchOnTurn(29, 1),
            List.of(REGISTEEL), List.of(new Moveset(LOCK_ON, List.of())), NEVER_SWITCH);
        assertEquals(List.of(48, 0), result.hpA());
        assertEquals(List.of(0), result.hpB());
    }

     /** Switches on each planned turn (turn -> team index); after a faint, brings in the first Pokémon alive. */
    private record SwitchPlan(Map<Integer, Integer> plan) implements TeamPolicy
    {
        public int chooseAction(int now, TeamView me, TeamView opponent) 
        {
            return plan.getOrDefault(now, FIGHT);
        }

        public int chooseReplacement(TeamView me, TeamView opponent)
        {
            return new SwitchOnTurn(-1, -1).chooseReplacement(me, opponent);
        }
    }

    @Test
    public void theSwitchTimerBlocksASecondSwitchFor45Seconds()
    {   // Turn 1: Medicham -> Altaria, starting A's 45 s (90 turn) timer. Turn 21: tries to switch back, refused.
        // Altaria beats Registeel alone, so Medicham is never hit (99). Without the timer it returns and ends at 40.
        TeamBattleResult result = TeamBattle.simulate(
            List.of(MEDICHAM, ALTARIA), List.of(new Moveset(COUNTER, List.of()), new Moveset(DRAGON_BREATH, List.of(SKY_ATTACK))),
            new SwitchPlan(Map.of(1, 1, 21, 0)),
            List.of(REGISTEEL), List.of(new Moveset(LOCK_ON, List.of())), NEVER_SWITCH);
        assertEquals(99, result.hpA().get(0));
        assertEquals(Winner.A, result.winner());
    }

     private static final Combatant SWAMPERT = new Combatant(SPECIES.findById("swampert").orElseThrow(), 6, 15, 12, 18.5, false);
    private static final Move MUD_SHOT     = MOVES.findById("MUD_SHOT").orElseThrow();     // 2 turns, 3 dmg to Registeel, 9 energy
    private static final Move EARTHQUAKE   = MOVES.findById("EARTHQUAKE").orElseThrow();
    private static final Move HYDRO_CANNON = MOVES.findById("HYDRO_CANNON").orElseThrow(); // 40 energy
    private static final Move FLASH_CANNON = MOVES.findById("FLASH_CANNON").orElseThrow();

    @Test
    public void aLosingLeadFarmsEnergyThenSwitchesBeforeTheOpponentsChargedMove()
    {   // Registeel's scenarios vs Swampert (PvPoke oracle): both bait 281.2, neither 311.1, no bait 311.1 -> overall 301.1.
        // SWITCH_BASIC = floor((500 - 301.1) / 10) = 19. Mud Shot costs Registeel 1.13% HP a turn, so
        // SWITCH_FARM = 19 x round(3 - 1.13)^2 = 76 and wins: Registeel keeps Lock-On-ing for energy.
        // Swampert reaches 45 energy on turn 10 (Hydro Cannon is 40), so on turn 11 the threat (39) passes 14% of
        // Registeel's HP and it switches out after 5 Mud Shots: 133 - 15 = 118. Altaria finishes Swampert.
        // SWITCH_BASIC would leave at 133 on turn 1; never switching, Registeel faints.
        TeamBattleResult result = TeamBattle.simulate(
            List.of(SWAMPERT), List.of(new Moveset(MUD_SHOT, List.of(EARTHQUAKE, HYDRO_CANNON))), NEVER_SWITCH,
            List.of(REGISTEEL, ALTARIA), List.of(new Moveset(LOCK_ON, List.of(FLASH_CANNON)), new Moveset(DRAGON_BREATH, List.of(SKY_ATTACK))),
            new TrainingAi());
        assertEquals(118, result.hpB().get(0));
        assertEquals(Winner.B, result.winner());
    }

    /** TrainingAI's shield decisions only: never switches voluntarily, first one alive after a faint. */
    private record ShieldsOnly(TeamPolicy ai) implements TeamPolicy
    {
        public int chooseAction(int now, TeamView me, TeamView opponent)
        {
            return FIGHT;
        }

        public int chooseReplacement(TeamView me, TeamView opponent)
        {
            return NEVER_SWITCH.chooseReplacement(me, opponent);
        }

        public Shield decideShield(TeamView me, TeamView attacker, BattleMove move, int attackerEnergy)
        {
            return ai.decideShield(me, attacker, move, attackerEnergy);
        }
    }

    private static final Move ICE_PUNCH = MOVES.findById("ICE_PUNCH").orElseThrow(); // 40 energy, 10 dmg to Registeel

    @Test
    public void theAiSavesItsShieldsInsteadOfBlockingChipDamage()
    {   // Each Ice Punch does 10 to Registeel: damageWeight = round(10 / max(hp, 66.5) x 10) = 1 or 2,
        // so yes = 4 and no = 4 + (8 - dw) x 2 = 16-18. no >= 4 x yes (BAD_DECISION_PROTECTION) -> no shield, three times.
        // Registeel faints sooner and the 9 HP Medicham behind it never gets to use the shields: Medicham ends at 43.
        // With the 1v1 rule (always shield), Registeel blocks two Ice Punches and Medicham ends at 38.
        TeamBattleResult result = TeamBattle.simulate(
            List.of(MEDICHAM), List.of(new Moveset(COUNTER, List.of(ICE_PUNCH))), NEVER_SWITCH,
            List.of(REGISTEEL, LEVEL_1_MEDICHAM), List.of(new Moveset(LOCK_ON, List.of()), new Moveset(COUNTER, List.of())),
            new ShieldsOnly(new TrainingAi()));
        assertEquals(List.of(43), result.hpA());
        assertEquals(List.of(0, 0), result.hpB());
    }

    @Test
    public void turnsCountThroughTheWholeBattleNotPerFight()
    {   // The 9 HP Medicham lands one Counter (99 -> 98) and faints on turn 2. Medicham then faces Registeel from turn 3,
        // takes a Lock-On every turn, and A's plan switches it out on battle turn 31: 98 - 28 (turns 3-30) = 70.
        // If the turn count restarted after the faint, the plan would fire on battle turn 33 and Medicham would leave at 68.
        TeamBattleResult result = TeamBattle.simulate(
            List.of(MEDICHAM, ALTARIA), List.of(new Moveset(COUNTER, List.of()), new Moveset(DRAGON_BREATH, List.of(SKY_ATTACK))),
            new SwitchPlan(Map.of(31, 1)),
            List.of(LEVEL_1_MEDICHAM, REGISTEEL), List.of(new Moveset(COUNTER, List.of()), new Moveset(LOCK_ON, List.of())), NEVER_SWITCH);
        assertEquals(70, result.hpA().get(0));
        assertEquals(Winner.A, result.winner());
    }


}

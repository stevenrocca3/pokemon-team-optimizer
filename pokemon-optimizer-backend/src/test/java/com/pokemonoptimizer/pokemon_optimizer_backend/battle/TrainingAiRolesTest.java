package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

/** A lineup (lead, safe swap, closer): switching out goes to the safe swap; a faint still brings in the best matchup. */
public class TrainingAiRolesTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();

    private static BattlePokemon pokemon(String id, int index, String fast, String... charged)
    {
        Combatant combatant = new Combatant(SPECIES.findById(id).orElseThrow(), 0, 15, 15, 20, false);
        Moveset moveset = new Moveset(MOVES.findById(fast).orElseThrow(), List.of(charged).stream().map(m -> MOVES.findById(m).orElseThrow()).toList());
        return new BattlePokemon(combatant, moveset, 2, index);
    }

    // Medicham leads into Registeel; Magikarp is the safe swap, Azumarill (which handles Registeel far better) the closer
    private final BattlePokemon medicham = pokemon("medicham", 0, "COUNTER", "ICE_PUNCH");
    private final BattlePokemon magikarp = pokemon("magikarp", 0, "SPLASH", "STRUGGLE");
    private final BattlePokemon azumarill = pokemon("azumarill", 0, "BUBBLE", "ICE_BEAM", "PLAY_ROUGH");
    private final List<BattlePokemon> lineup = List.of(medicham, magikarp, azumarill);
    private final BattlePokemon registeel = pokemon("registeel", 1, "LOCK_ON", "FOCUS_BLAST");
    private final TeamView them = new TeamView(List.of(registeel), registeel, 0);

    @Test
    public void withoutRolesTheAiSwitchesToTheBestMatchup()
    {
        assertEquals(2, new TrainingAi(false).switchTarget(new TeamView(lineup, medicham, 0), them));
    }

    @Test
    public void withRolesSwitchingOutGoesToTheSafeSwap()
    {
        assertEquals(1, new TrainingAi(true).switchTarget(new TeamView(lineup, medicham, 0), them));
    }

    @Test
    public void aFaintedSafeSwapLeavesTheChoiceToTheAi()
    {
        magikarp.hp = 0;

        assertEquals(2, new TrainingAi(true).switchTarget(new TeamView(lineup, medicham, 0), them));
    }

    @Test
    public void theSafeSwapItselfSwitchesOutToTheBestMatchup()
    {
        TeamView me = new TeamView(lineup, magikarp, 0);

        assertEquals(new TrainingAi(false).switchTarget(me, them), new TrainingAi(true).switchTarget(me, them));
    }

    @Test
    public void aTeamBattleWithRolesSendsTheSafeSwapIn()
    {   // Medicham leads into Azumarill and switches out: without roles Magikarp never battles; with them it's the switch-in and faints
        List<Combatant> lineup = List.of(medicham.combatant, magikarp.combatant, pokemon("registeel", 0, "LOCK_ON", "FOCUS_BLAST").combatant);
        List<Moveset> movesets = List.of(medicham.moveset, magikarp.moveset, registeel.moveset);
        List<Combatant> foes = List.of(azumarill.combatant);
        List<Moveset> foeMoves = List.of(azumarill.moveset);

        TeamBattleResult plain = TeamBattle.simulate(lineup, movesets, foes, foeMoves);
        TeamBattleResult roles = TeamBattle.simulateWithRoles(lineup, movesets, foes, foeMoves);

        assertEquals(magikarp.combatant.maxHp(), plain.hpA().get(1));
        assertEquals(0, roles.hpA().get(1));
    }

    @Test
    public void afterAFaintTheBestMatchupComesInEvenWithRoles()
    {
        medicham.hp = 0;

        assertEquals(2, new TrainingAi(true).chooseReplacement(new TeamView(lineup, medicham, 0), them));
    }
}

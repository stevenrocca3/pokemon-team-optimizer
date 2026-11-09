package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

/**
 * TrainingAi remembers scenario ratings. Each test first fills the cache from one position, then changes one thing a
 * scenario depends on: the remembered answer must not come back for the changed position.
 */
public class ScenarioCacheTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();
    private static final Combatant MEDICHAM = new Combatant(SPECIES.findById("medicham").orElseThrow(), 4, 14, 9, 20.5, false);
    private static final Combatant AZUMARILL = new Combatant(SPECIES.findById("azumarill").orElseThrow(), 0, 14, 15, 40, false);
    private static final Moveset MEDICHAM_MOVES = new Moveset(MOVES.findById("COUNTER").orElseThrow(),
        List.of(MOVES.findById("ICE_PUNCH").orElseThrow(), MOVES.findById("PSYCHIC").orElseThrow()));
    private static final Moveset AZUMARILL_MOVES = new Moveset(MOVES.findById("BUBBLE").orElseThrow(),
        List.of(MOVES.findById("ICE_BEAM").orElseThrow(), MOVES.findById("PLAY_ROUGH").orElseThrow()));

    private static BattlePokemon medicham()
    {
        return new BattlePokemon(MEDICHAM, MEDICHAM_MOVES, 2, 0);
    }

    private static BattlePokemon azumarill()
    {
        return new BattlePokemon(AZUMARILL, AZUMARILL_MOVES, 2, 1);
    }

    /** Warm the cache with the base position, change it, and compare the cached answer with a fresh simulation. */
    private static void changedPositionIsSimulatedAgain(Consumer<BattlePokemon> changeMine, Consumer<BattlePokemon> changeTheirs, int myShields)
    {
        for (String type : List.of("BOTH_BAIT", "NO_BAIT", "FARM"))
        {
            TrainingAi.scenario(type, medicham(), 2, azumarill());   // what a stale cache would answer with

            BattlePokemon mine = medicham();
            BattlePokemon theirs = azumarill();
            changeMine.accept(mine);
            changeTheirs.accept(theirs);

            assertEquals(TrainingAi.simulateScenario(type, mine, myShields, theirs), TrainingAi.scenario(type, mine, myShields, theirs), type);
        }
    }

    private static final Consumer<BattlePokemon> SAME = p -> { };

    @Test
    public void hp() { changedPositionIsSimulatedAgain(p -> p.hp = 40, SAME, 2); }

    @Test
    public void energy() { changedPositionIsSimulatedAgain(p -> p.energy = 55, SAME, 2); }

    @Test
    public void cooldown() { changedPositionIsSimulatedAgain(SAME, p -> p.cooldown = 1000, 2); }

    @Test
    public void statStages() { changedPositionIsSimulatedAgain(p -> p.setStatBuffs(new int[] { 1, -1 }), SAME, 2); }

    @Test
    public void myShields() { changedPositionIsSimulatedAgain(SAME, SAME, 0); }

    @Test
    public void theirShields() { changedPositionIsSimulatedAgain(SAME, p -> p.shields = 0, 2); }

    @Test
    public void theirHpAndEnergy() { changedPositionIsSimulatedAgain(SAME, p -> { p.hp = 60; p.energy = 70; }, 2); }

    @Test
    public void anotherPokemonWithTheSameStats()
    {   // identity, not equality: a different Combatant object is a different entry
        Combatant twin = new Combatant(MEDICHAM.species(), 15, 15, 15, 40, false);
        changedPositionIsSimulatedAgain(SAME, SAME, 2);
        BattlePokemon other = new BattlePokemon(twin, MEDICHAM_MOVES, 2, 0);
        assertEquals(TrainingAi.simulateScenario("NO_BAIT", other, 2, azumarill()), TrainingAi.scenario("NO_BAIT", other, 2, azumarill()));
    }
}

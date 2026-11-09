package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Combatant;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.DamageCalculator;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.TypeChart;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMovesets;

public class BreakpointsTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();
    private static final MetaLookup META = new MetaLookup(SPECIES, MOVES);
    private static final Breakpoints BREAKPOINTS = new Breakpoints(
        new TeamBuilder(SPECIES, MOVES, META, new GoodIvs(SPECIES, new SuggestedMovesets(SPECIES))), META);

    private static final CaughtPokemonRequest MEDICHAM =
        new CaughtPokemonRequest("medicham", 0, 15, 15, null, "COUNTER", List.of("ICE_PUNCH"), false);
    private static final SpeciesType MEDICHAM_SPECIES = SPECIES.findById("medicham").orElseThrow();
    private static final Move COUNTER = MOVES.findById("COUNTER").orElseThrow();

    /** Counter's damage to a meta Pokémon if Medicham's Attack stat were exactly this. */
    private static int counterAt(double attack, Combatant defender)
    {
        boolean stab = MEDICHAM_SPECIES.types().contains(COUNTER.type());
        return DamageCalculator.damage(COUNTER.power(), attack, defender.defense(), stab,
            TypeChart.effectiveness(COUNTER.type(), defender.species().types()));
    }

    @Test
    public void itIsBuiltForTheLeagueAndCoversTheWholeMeta()
    {
        BreakpointReport report = BREAKPOINTS.report(League.GREAT, MEDICHAM);

        assertEquals(50.0, report.level());
        assertEquals(1439, report.cp());
        assertEquals(44, report.opponents().size());
        assertTrue(report.maxAttack() >= report.attack() && report.maxDefense() >= report.defense(), report.toString());
    }

    @Test
    public void damageAndBreakpointsAreExact()
    {   // at the breakpoint Counter does one more; a hair below it, it doesn't
        BreakpointReport report = BREAKPOINTS.report(League.GREAT, MEDICHAM);
        List<MetaPokemon> meta = META.meta(League.GREAT);
        int found = 0;
        for (int m = 0; m < meta.size(); m++)
        {
            OpponentBreakpoint row = report.opponents().get(m);
            Combatant defender = meta.get(m).combatant();
            assertEquals(meta.get(m).id(), row.opponent());
            assertEquals(counterAt(report.attack(), defender), row.damageDealt());
            if (row.attackForMore() != null)
            {
                found++;
                assertTrue(row.attackForMore() > report.attack() && row.attackForMore() <= report.maxAttack(), row.toString());
                assertEquals(row.damageDealt() + 1, counterAt(row.attackForMore(), defender), row.toString());
                assertEquals(row.damageDealt(), counterAt(Math.nextDown(row.attackForMore()), defender), row.toString());
            }
        }
        assertTrue(found > 0, "a 0-attack Medicham has breakpoints a higher-attack spread reaches");
    }

    @Test
    public void bulkpointsAreExact()
    {   // at the bulkpoint their fast move does one less to Medicham; a hair below it, it doesn't
        BreakpointReport report = BREAKPOINTS.report(League.GREAT, MEDICHAM);
        List<MetaPokemon> meta = META.meta(League.GREAT);
        Combatant medicham = new Combatant(MEDICHAM_SPECIES, 0, 15, 15, 50, false);
        for (int m = 0; m < meta.size(); m++)
        {
            OpponentBreakpoint row = report.opponents().get(m);
            Move fast = meta.get(m).moveset().fastMove();
            Combatant attacker = meta.get(m).combatant();
            assertEquals(fast.moveId(), row.opponentFastMove());
            assertEquals(DamageCalculator.damage(attacker, medicham, fast), row.damageTaken());
            if (row.defenseForLess() != null)
            {
                boolean stab = attacker.species().types().contains(fast.type());
                double eff = TypeChart.effectiveness(fast.type(), MEDICHAM_SPECIES.types());
                assertTrue(row.defenseForLess() > report.defense() && row.defenseForLess() <= report.maxDefense(), row.toString());
                assertEquals(row.damageTaken() - 1, DamageCalculator.damage(fast.power(), attacker.attack(), row.defenseForLess(), stab, eff));
                assertEquals(row.damageTaken(), DamageCalculator.damage(fast.power(), attacker.attack(), Math.nextDown(row.defenseForLess()), stab, eff));
            }
        }
    }

    @Test
    public void oneThatCantPlayIsRefusedWithTheReason()
    {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> BREAKPOINTS.report(League.GREAT,
            new CaughtPokemonRequest("medicham", 15, 15, 15, 50.0, "COUNTER", List.of("ICE_PUNCH"), false)));
        assertEquals("Over 1500 CP", e.getMessage());
    }
}

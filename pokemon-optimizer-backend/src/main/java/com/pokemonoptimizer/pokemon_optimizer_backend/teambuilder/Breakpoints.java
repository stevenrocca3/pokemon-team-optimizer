package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleToIntFunction;

import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Combatant;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.DamageCalculator;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.TypeChart;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

/**
 * Damage breakpoints and bulkpoints for a Pokémon against a League's meta (see GLOSSARY.md). Only thresholds some
 * IV spread of the species can reach under the Cap are reported: those are the ones choosing a different copy
 * could buy you.
 */
@Component
public class Breakpoints
{
    /** Highest in-battle Attack and Defense any spread reaches under a Cap, per species, Shadow and League. */
    private record StatCeiling(double attack, double defense) {}

    private final TeamBuilder teamBuilder;
    private final MetaLookup metaLookup;
    private final Map<String, StatCeiling> ceilings = new ConcurrentHashMap<>();

    public Breakpoints(TeamBuilder teamBuilder, MetaLookup metaLookup)
    {
        this.teamBuilder = teamBuilder;
        this.metaLookup = metaLookup;
    }

    /** Throws IllegalArgumentException with the reason when it can't play in the League. */
    public BreakpointReport report(League league, CaughtPokemonRequest pokemon)
    {
        String problem = teamBuilder.problemWith(pokemon, league);
        if (problem != null)
        {
            throw new IllegalArgumentException(problem);
        }
        TeamBuilder.Candidate candidate = teamBuilder.candidate(0, pokemon, false, league);
        Combatant you = candidate.combatant();
        Move yourFast = candidate.moveset().fastMove();
        StatCeiling ceiling = ceilings.computeIfAbsent(you.species().speciesId() + "/" + you.shadow() + "/" + league,
            key -> ceiling(you.species(), you.shadow(), league));

        List<OpponentBreakpoint> rows = new ArrayList<>();
        for (MetaPokemon opponent : metaLookup.meta(league))
        {
            Combatant them = opponent.combatant();
            Move theirFast = opponent.moveset().fastMove();

            DoubleToIntFunction dealtAt = attack -> damage(yourFast, you.species(), attack, them.defense(), them.species());
            int dealt = dealtAt.applyAsInt(you.attack());
            Double attackForMore = dealtAt.applyAsInt(ceiling.attack()) > dealt
                ? lowestWhere(you.attack(), ceiling.attack(), a -> dealtAt.applyAsInt(a) > dealt) : null;

            DoubleToIntFunction takenAt = defense -> damage(theirFast, them.species(), them.attack(), defense, you.species());
            int taken = takenAt.applyAsInt(you.defense());
            Double defenseForLess = takenAt.applyAsInt(ceiling.defense()) < taken
                ? lowestWhere(you.defense(), ceiling.defense(), d -> takenAt.applyAsInt(d) < taken) : null;

            rows.add(new OpponentBreakpoint(opponent.id(), theirFast.moveId(), dealt, attackForMore, taken, defenseForLess));
        }
        return new BreakpointReport(league, you.level(), candidate.cp(), yourFast.moveId(), you.attack(), you.defense(),
            ceiling.attack(), ceiling.defense(), List.copyOf(rows));
    }

    private static int damage(Move move, SpeciesType attacker, double attack, double defense, SpeciesType defender)
    {
        boolean stab = attacker.types().contains(move.type());
        return DamageCalculator.damage(move.power(), attack, defense, stab, TypeChart.effectiveness(move.type(), defender.types()));
    }

    /**
     * The smallest stat in (low, high] where {@code reached} holds, given it holds at {@code high} and not at
     * {@code low}. Bisects down to adjacent doubles, so it's exact whatever the damage formula's rounding.
     */
    private static double lowestWhere(double low, double high, java.util.function.DoublePredicate reached)
    {
        while (true)
        {
            double mid = low + (high - low) / 2;
            if (mid <= low || mid >= high)
            {
                return high;
            }
            if (reached.test(mid)) high = mid;
            else low = mid;
        }
    }

    private static StatCeiling ceiling(SpeciesType species, boolean shadow, League league)
    {
        double attack = 0;
        double defense = 0;
        for (int a = 0; a < 16; a++)
        {
            for (int d = 0; d < 16; d++)
            {
                for (int s = 0; s < 16; s++)
                {
                    double level = league.buildLevel(species, a, d, s, 1.0).orElseThrow();
                    Combatant built = new Combatant(species, a, d, s, level, shadow);
                    attack = Math.max(attack, built.attack());
                    defense = Math.max(defense, built.defense());
                }
            }
        }
        return new StatCeiling(attack, defense);
    }
}

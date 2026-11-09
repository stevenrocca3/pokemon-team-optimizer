package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Battle;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.BattleResult;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.ShieldScenario;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Winner;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;

/**
 * Which of a few Pokémon (usually copies of one species) is worth powering up for a League: each is built to
 * its best level under the Cap, like the Team Builder does, then battles every meta Pokémon at 0, 1 and 2 shields.
 * The battles where they part ways are the flips, the reason one copy is better than another.
 */
@Component
public class IvComparison
{
    private static final int[] SHIELDS = { 0, 1, 2 };

    private final TeamBuilder teamBuilder;
    private final MetaLookup metaLookup;

    public IvComparison(TeamBuilder teamBuilder, MetaLookup metaLookup)
    {
        this.teamBuilder = teamBuilder;
        this.metaLookup = metaLookup;
    }

    public IvComparisonResult compare(League league, List<CaughtPokemonRequest> pokemon)
    {
        List<MetaPokemon> meta = metaLookup.meta(league);
        int battles = meta.size() * SHIELDS.length;

        // won[p][battle]: null when Pokémon p can't play in the League; battle = opponent * 3 + shield scenario
        boolean[][] won = new boolean[pokemon.size()][];
        double[] rating = new double[pokemon.size()];
        List<ComparedPokemon> compared = new ArrayList<>(IntStream.range(0, pokemon.size()).parallel().mapToObj(p -> {
            String problem = teamBuilder.problemWith(pokemon.get(p), league);
            if (problem != null)
            {
                return new ComparedPokemon(p, 0, 0, 0, battles, 0, problem);
            }
            TeamBuilder.Candidate candidate = teamBuilder.candidate(p, pokemon.get(p), false, league);
            won[p] = new boolean[battles];
            double total = 0;
            int wins = 0;
            for (int m = 0; m < meta.size(); m++)
            {
                for (int s = 0; s < SHIELDS.length; s++)
                {
                    BattleResult result = Battle.simulate(candidate.combatant(), candidate.moveset(), meta.get(m).combatant(),
                        meta.get(m).moveset(), new ShieldScenario(SHIELDS[s], SHIELDS[s]));
                    total += result.ratingA();
                    if (result.winner() == Winner.A)
                    {
                        won[p][m * SHIELDS.length + s] = true;
                        wins++;
                    }
                }
            }
            rating[p] = total / battles;
            return new ComparedPokemon(p, candidate.combatant().level(), candidate.cp(), wins, battles,
                Math.round(rating[p] * 10) / 10.0, null);
        }).toList());

        // the ones that can play first, then most wins, then the better average rating; request order breaks ties
        compared.sort(Comparator.comparing((ComparedPokemon c) -> c.problem() != null)
            .thenComparing(Comparator.comparingInt(ComparedPokemon::wins).reversed())
            .thenComparing(Comparator.comparingDouble((ComparedPokemon c) -> rating[c.index()]).reversed())
            .thenComparingInt(ComparedPokemon::index));

        List<Integer> playing = IntStream.range(0, pokemon.size()).filter(p -> won[p] != null).boxed().toList();
        List<FlippedMatchup> flips = new ArrayList<>();
        for (int b = 0; b < battles; b++)
        {
            int battle = b;
            List<Integer> winners = playing.stream().filter(p -> won[p][battle]).toList();
            if (!winners.isEmpty() && winners.size() < playing.size())
            {
                flips.add(new FlippedMatchup(meta.get(b / SHIELDS.length).id(), SHIELDS[b % SHIELDS.length], winners));
            }
        }
        return new IvComparisonResult(league, meta.size(), metaLookup.snapshotDate(), List.copyOf(compared), List.copyOf(flips));
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Combatant;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;

/**
 * Swaps for a Team winning under half its battles (#112): the 5 meta Pokémon that raise its meta coverage most when
 * they take one member's place. Coverage is what the Team Builder shortlists on: for each meta Pokémon, the best
 * rating any member gets against it, averaged. Everything comes from the kept {@link MetaMatrix} and the ratings the
 * search already has, so it's arithmetic on a few thousand numbers, no battles.
 */
final class Replacements
{
    static final double THRESHOLD = 0.5;
    static final int COUNT = 5;
    private static final int COVERS_SHOWN = 3;
    /** MetaBattles.SHIELDS index → role: none left closes, one each takes a switch, two each leads */
    private static final Suggestion.Role[] ROLE_AT = { Suggestion.Role.CLOSER, Suggestion.Role.SAFE_SWAP, Suggestion.Role.LEAD };

    /** A Team member: its request index, species, Pokédex number and average rating against each meta Pokémon. */
    record Member(int index, String speciesId, int dex, double[] ratings) {}

    /** The best swap for one meta Pokémon: who it replaces and what the Team's coverage becomes. */
    private record Swap(int candidate, int replaced, double coverage) {}

    private Replacements() {}

    static boolean needed(WinRate odds)
    {
        return odds.rate() < THRESHOLD;
    }

    static List<Suggestion> suggest(MetaMatrix matrix, List<Member> team)
    {
        List<MetaPokemon> meta = matrix.meta();
        double now = coverage(team.stream().map(Member::ratings).toList(), meta.size());
        Set<Integer> taken = new HashSet<>();
        team.forEach(member -> taken.add(member.dex()));

        List<Swap> best = new ArrayList<>();
        for (int c = 0; c < meta.size(); c++)
        {
            if (taken.contains(dex(meta.get(c))))   // GO Battle League bans two of one species
            {
                continue;
            }
            double[] ratings = row(matrix, c);
            Swap swap = null;
            for (int r = 0; r < team.size(); r++)
            {
                List<double[]> swapped = new ArrayList<>();
                for (int i = 0; i < team.size(); i++)
                {
                    swapped.add(i == r ? ratings : team.get(i).ratings());
                }
                double coverage = coverage(swapped, meta.size());
                if (swap == null || coverage > swap.coverage())
                {
                    swap = new Swap(c, r, coverage);
                }
            }
            best.add(swap);
        }

        // best first; a Shadow and a regular of one species are one pick, the better of the two
        List<Suggestion> suggestions = new ArrayList<>();
        Set<Integer> suggested = new HashSet<>();
        best.sort(Comparator.comparingDouble((Swap s) -> -s.coverage()).thenComparingInt(Swap::candidate));
        for (Swap swap : best)
        {
            if (suggestions.size() < COUNT && suggested.add(dex(meta.get(swap.candidate()))))
            {
                suggestions.add(suggestion(matrix, swap, team, now));
            }
        }
        return suggestions;
    }

    private static Suggestion suggestion(MetaMatrix matrix, Swap swap, List<Member> team, double coverageBefore)
    {
        MetaPokemon pick = matrix.meta().get(swap.candidate());
        Member replaced = team.get(swap.replaced());
        Combatant combatant = pick.combatant();
        int metaSize = matrix.meta().size();

        // what it adds against each meta Pokémon, over the best answer the Team had already
        double[] ratings = row(matrix, swap.candidate());
        double[] before = new double[metaSize];
        for (int m = 0; m < metaSize; m++)
        {
            for (Member member : team)
            {
                before[m] = Math.max(before[m], member.ratings()[m]);
            }
        }
        List<String> covers = IntStream.range(0, metaSize).boxed()
            .filter(m -> ratings[m] > before[m])
            .sorted(Comparator.comparingDouble((Integer m) -> -(ratings[m] - before[m])).thenComparingInt(m -> m))
            .limit(COVERS_SHOWN)
            .map(m -> matrix.meta().get(m).id())
            .toList();

        int bestShields = 0;
        for (int s = 1; s < ROLE_AT.length; s++)
        {
            if (matrix.ratingAtShields(swap.candidate(), s) > matrix.ratingAtShields(swap.candidate(), bestShields))
            {
                bestShields = s;
            }
        }

        return new Suggestion(combatant.species().speciesId(), combatant.shadow(), ROLE_AT[bestShields], replaced.index(),
            replaced.speciesId(), combatant.level(),
            League.cp(combatant.species(), combatant.atkIv(), combatant.defIv(), combatant.staIv(), combatant.level()),
            combatant.atkIv(), combatant.defIv(), combatant.staIv(), pick.moveset().fastMove().moveId(),
            pick.moveset().chargedMoves().stream().map(Move::moveId).toList(),
            Math.round((swap.coverage() - coverageBefore) * 10) / 10.0, covers);
    }

    /** For each meta Pokémon, the best rating any of these gets against it; averaged. */
    private static double coverage(List<double[]> members, int metaSize)
    {
        double total = 0;
        for (int m = 0; m < metaSize; m++)
        {
            double best = 0;
            for (double[] ratings : members)
            {
                best = Math.max(best, ratings[m]);
            }
            total += best;
        }
        return total / metaSize;
    }

    private static double[] row(MetaMatrix matrix, int c)
    {
        int size = matrix.meta().size();
        double[] ratings = new double[size];
        for (int m = 0; m < size; m++)
        {
            ratings[m] = matrix.rating(c, m);
        }
        return ratings;
    }

    private static int dex(MetaPokemon pokemon)
    {
        return pokemon.combatant().species().dex();
    }
}

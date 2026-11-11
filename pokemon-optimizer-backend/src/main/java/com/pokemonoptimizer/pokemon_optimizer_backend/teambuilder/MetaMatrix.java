package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;
import java.util.stream.IntStream;

import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Battle;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.ShieldScenario;

/**
 * Every meta Pokémon's 1v1 battle rating against every other, at 0, 1 and 2 shields each (#112). Worked out once per
 * meta and kept (see {@link MetaMatrices}), so suggesting replacements is a lookup, not a simulation.
 * {@code byShield[c][m][s]}: meta Pokémon {@code c} against {@code m} with both sides on {@code MetaBattles.SHIELDS[s]}.
 */
public final class MetaMatrix
{
    private final List<MetaPokemon> meta;
    private final double[][][] byShield;
    private final double[][] average;

    MetaMatrix(List<MetaPokemon> meta, double[][][] byShield)
    {
        this.meta = meta;
        this.byShield = byShield;
        this.average = new double[byShield.length][];
        for (int c = 0; c < byShield.length; c++)
        {
            average[c] = new double[byShield[c].length];
            for (int m = 0; m < byShield[c].length; m++)
            {
                double total = 0;
                for (double rating : byShield[c][m])
                {
                    total += rating;
                }
                average[c][m] = total / byShield[c][m].length;
            }
        }
    }

    /** Battles the meta against itself: meta size² × 3 1v1s, in parallel. */
    static MetaMatrix of(List<MetaPokemon> meta, int battleCp)
    {
        int[] shields = MetaBattles.SHIELDS;
        double[][][] ratings = IntStream.range(0, meta.size()).parallel()
            .mapToObj(c -> {
                double[][] row = new double[meta.size()][shields.length];
                for (int m = 0; m < meta.size(); m++)
                {
                    for (int s = 0; s < shields.length; s++)
                    {
                        row[m][s] = Battle.simulate(meta.get(c).combatant(), meta.get(c).moveset(), meta.get(m).combatant(),
                            meta.get(m).moveset(), new ShieldScenario(shields[s], shields[s]), battleCp).ratingA();
                    }
                }
                return row;
            })
            .toArray(double[][][]::new);
        return new MetaMatrix(meta, ratings);
    }

    List<MetaPokemon> meta()
    {
        return meta;
    }

    /** Rating of meta Pokémon {@code c} against {@code m}, averaged over the shield scenarios. */
    double rating(int c, int m)
    {
        return average[c][m];
    }

    /** Meta Pokémon {@code c}'s average rating against the whole meta at shield scenario {@code s}. */
    double ratingAtShields(int c, int s)
    {
        double total = 0;
        for (double[] row : byShield[c])
        {
            total += row[s];
        }
        return total / byShield[c].length;
    }
}

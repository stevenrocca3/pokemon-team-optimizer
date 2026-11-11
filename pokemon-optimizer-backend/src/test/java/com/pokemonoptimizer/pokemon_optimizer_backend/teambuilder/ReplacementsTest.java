package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

public class ReplacementsTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();
    private static final MetaLookup META_LOOKUP = new MetaLookup(SPECIES, MOVES);
    private static final List<MetaPokemon> META = META_LOOKUP.meta(League.GREAT);
    private static final int THREATS_FROM = 10;   // meta Pokémon 10, 11 and 12 beat the test team
    private static final int THREATS_TO = 13;

    private static int dex(int metaIndex)
    {
        return META.get(metaIndex).combatant().species().dex();
    }

    /** Every meta Pokémon rates {@code flat} against everything, at every shield count. */
    private static double[][][] flat(double flat)
    {
        double[][][] ratings = new double[META.size()][META.size()][3];
        for (double[][] row : ratings)
        {
            for (double[] cell : row)
            {
                Arrays.fill(cell, flat);
            }
        }
        return ratings;
    }

    private static double[] ratings(double everywhere, double againstThreats)
    {
        double[] ratings = new double[META.size()];
        Arrays.fill(ratings, everywhere);
        Arrays.fill(ratings, THREATS_FROM, THREATS_TO, againstThreats);
        return ratings;
    }

    /**
     * Meta 0-2's species: two solid members, each the only answer to something (member 0 to meta 30-32, where member 1
     * rates 300), and one that loses to the threats (500, and 200 against them).
     */
    private static List<Replacements.Member> weakTeam()
    {
        double[] second = ratings(650, 650);
        Arrays.fill(second, 30, 33, 300);
        return List.of(
            new Replacements.Member(0, META.get(0).id(), dex(0), ratings(600, 600)),
            new Replacements.Member(1, META.get(1).id(), dex(1), second),
            new Replacements.Member(2, META.get(2).id(), dex(2), ratings(500, 200)));
    }

    // The trigger: a Team under 50% gets suggestions, one at 50% or over doesn't
    @Test
    public void onlyTeamsUnderHalfGetSuggestions()
    {
        assertTrue(Replacements.needed(WinRate.of(199, 400)));
        assertFalse(Replacements.needed(WinRate.of(200, 400)));
        assertFalse(Replacements.needed(WinRate.of(300, 400)));
    }

    // Exactly 5 suggestions, all different species, none already on the Team
    @Test
    public void fiveSuggestionsOfNewSpecies()
    {
        List<Suggestion> suggestions = Replacements.suggest(new MetaMatrix(META, flat(500)), weakTeam());

        assertEquals(5, suggestions.size());
        Set<Integer> dexes = new HashSet<>();
        for (Suggestion s : suggestions)
        {
            int dex = SPECIES.findById(s.speciesId()).orElseThrow().dex();
            assertFalse(Set.of(dex(0), dex(1), dex(2)).contains(dex), s.speciesId() + " is already on the Team");
            assertTrue(dexes.add(dex), s.speciesId() + " is suggested twice");
        }
    }

    // The meta Pokémon that beats the Team's threats comes first, swapped for the member those threats beat
    @Test
    public void theThreatAnswerComesFirstAndReplacesTheWeakMember()
    {
        double[][][] ratings = flat(400);
        for (int m = THREATS_FROM; m < THREATS_TO; m++)
        {
            Arrays.fill(ratings[20][m], 900);
        }

        Suggestion first = Replacements.suggest(new MetaMatrix(META, ratings), weakTeam()).get(0);

        MetaPokemon answer = META.get(20);
        assertEquals(answer.combatant().species().speciesId(), first.speciesId());
        assertEquals(answer.combatant().shadow(), first.shadow());
        assertEquals(2, first.replaces());
        assertEquals(META.get(2).id(), first.replacesSpeciesId());
        // coverage rises by (900 - 650) on 3 of the meta: member 1 already answers the threats at 650
        assertEquals(Math.round(3 * 250.0 / META.size() * 10) / 10.0, first.coverageGain());
        assertEquals(Set.of(META.get(10).id(), META.get(11).id(), META.get(12).id()), new HashSet<>(first.covers()));
    }

    // A suggestion is built the way PvPoke battles it: the meta's IVs, level and moveset
    @Test
    public void aSuggestionIsTheMetaBuild()
    {
        double[][][] ratings = flat(400);
        Arrays.fill(ratings[20][THREATS_FROM], 900);

        Suggestion first = Replacements.suggest(new MetaMatrix(META, ratings), weakTeam()).get(0);

        MetaPokemon answer = META.get(20);
        assertEquals(answer.combatant().level(), first.level());
        assertEquals(answer.combatant().atkIv(), first.atkIv());
        assertEquals(answer.combatant().defIv(), first.defIv());
        assertEquals(answer.combatant().staIv(), first.staIv());
        assertEquals(League.cp(answer.combatant().species(), first.atkIv(), first.defIv(), first.staIv(), first.level()), first.cp());
        assertEquals(answer.moveset().fastMove().moveId(), first.fastMove());
        assertEquals(answer.moveset().chargedMoves().stream().map(Move::moveId).toList(), first.chargedMoves());
    }

    // Its role is the shield count it does best at: none left is a closer, one each a safe swap, two each a lead
    @Test
    public void roleFollowsTheShieldCountItDoesBestAt()
    {
        for (int s = 0; s < 3; s++)
        {
            double[][][] ratings = flat(400);
            for (int m = THREATS_FROM; m < THREATS_TO; m++)
            {
                Arrays.fill(ratings[20][m], 700);
                ratings[20][m][s] = 1000;   // still the best answer, best of all at this shield count
            }

            Suggestion first = Replacements.suggest(new MetaMatrix(META, ratings), weakTeam()).get(0);

            Suggestion.Role expected = List.of(Suggestion.Role.CLOSER, Suggestion.Role.SAFE_SWAP, Suggestion.Role.LEAD).get(s);
            assertEquals(expected, first.role(), "best at " + s + " shields");
        }
    }

    // The matrix is worked out once per meta and kept, and suggesting from it adds under 50 ms
    @Test
    public void suggestingFromTheKeptMatrixTakesUnder50Ms()
    {
        MetaMatrices matrices = new MetaMatrices();
        MetaMatrix matrix = matrices.get("GREAT", META, League.GREAT.battleCp());
        assertSame(matrix, matrices.get("GREAT", META, League.GREAT.battleCp()));

        List<Replacements.Member> team = List.of(
            new Replacements.Member(0, META.get(0).id(), dex(0), row(matrix, 0)),
            new Replacements.Member(1, META.get(5).id(), dex(5), row(matrix, 5)),
            new Replacements.Member(2, META.get(9).id(), dex(9), row(matrix, 9)));
        Replacements.suggest(matrix, team);   // class loading and JIT aside
        long started = System.nanoTime();
        List<Suggestion> suggestions = Replacements.suggest(matrix, team);
        long ms = (System.nanoTime() - started) / 1_000_000;

        assertEquals(5, suggestions.size());
        assertTrue(ms < 50, "took " + ms + " ms");
    }

    private static double[] row(MetaMatrix matrix, int c)
    {
        double[] ratings = new double[META.size()];
        for (int m = 0; m < META.size(); m++)
        {
            ratings[m] = matrix.rating(c, m);
        }
        return ratings;
    }
}

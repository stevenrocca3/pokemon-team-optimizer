package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Battle;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Combatant;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Moveset;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.ShieldScenario;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.TeamBattle;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Winner;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

/**
 * Finds the best Teams of three from a set of Pokémon, ranked by sampled 3v3 win odds (ADR 0005):
 * screen with 1v1s against the meta, shortlist Teams by coverage, pick each one's lead on a selection sample,
 * then estimate the finalists' odds on a fresh sample. Battles run in parallel; every result is a sum of
 * whole and half wins, so the order they finish in can't change the answer.
 */
@Component
public class TeamBuilder
{
    /**
     * How wide each search stage is: species fully rated after the 1-1 screen, species Teams are built from,
     * finalist Teams, and the two sample sizes. {@code seed} fixes the sampled opponents so results repeat.
     */
    record Settings(int screenPool, int shortlistSpecies, int teamsToSimulate, int selectionSamples, int estimateSamples,
                    int results, long seed)
    {
        static final Settings DEFAULT = new Settings(30, 10, 5, 100, 400, 3, 2026);
    }

    private static final int SCREEN_SHIELDS = 1;
    private static final int[] SHIELDS = { 0, 1, 2 };
    private static final int TOUGHEST_SHOWN = 3;

    /** An eligible Pokémon from the request, built for the League. Shared with IvComparison. */
    record Candidate(int index, CaughtPokemonRequest request, boolean assumed, SpeciesType species, Combatant combatant, Moveset moveset, int cp) {}

    /** A Team being considered: three shortlist positions, and its 1v1 coverage of the meta. */
    private record Trio(int[] members, double coverage) {}

    /** An opposing Team, lead first. */
    private record Opponents(List<Combatant> combatants, List<Moveset> movesets) {}

    private final SpeciesLookup speciesLookup;
    private final MoveLookup moveLookup;
    private final MetaLookup metaLookup;
    private final GoodIvs goodIvs;
    private final Settings settings;

    @Autowired   // the other constructor is for tests' narrower Settings
    public TeamBuilder(SpeciesLookup speciesLookup, MoveLookup moveLookup, MetaLookup metaLookup, GoodIvs goodIvs)
    {
        this(speciesLookup, moveLookup, metaLookup, goodIvs, Settings.DEFAULT);
    }

    TeamBuilder(SpeciesLookup speciesLookup, MoveLookup moveLookup, MetaLookup metaLookup, GoodIvs goodIvs, Settings settings)
    {
        this.speciesLookup = speciesLookup;
        this.moveLookup = moveLookup;
        this.metaLookup = metaLookup;
        this.goodIvs = goodIvs;
        this.settings = settings;
    }

    public TeamBuilderResult build(League league, List<CaughtPokemonRequest> pokemon)
    {
        return build(league, pokemon, List.of());
    }

    /**
     * Teams from the Trainer's exact Pokémon plus the species they ticked on the checklist. Indexes in the result
     * count {@code pokemon} first, then {@code species}: species {@code s} is index {@code pokemon.size() + s}.
     */
    public TeamBuilderResult build(League league, List<CaughtPokemonRequest> pokemon, List<OwnedSpecies> species)
    {
        List<MetaPokemon> meta = metaLookup.meta(league);
        List<SkippedPokemon> skipped = new ArrayList<>();
        List<Candidate> eligible = new ArrayList<>();
        for (int i = 0; i < pokemon.size(); i++)
        {
            consider(i, pokemon.get(i), false, league, eligible, skipped);
        }
        // a ticked species the Trainer also entered exactly is theirs already; the real one counts, not a guess
        Set<String> entered = new HashSet<>();
        pokemon.forEach(p -> entered.add(p.speciesId() + "/" + p.shadow()));
        for (int s = 0; s < species.size(); s++)
        {
            OwnedSpecies owned = species.get(s);
            if (entered.contains(owned.speciesId() + "/" + owned.shadow()))
            {
                continue;
            }
            int index = pokemon.size() + s;
            Optional<CaughtPokemonRequest> assumed = goodIvs.assume(owned, league);
            if (assumed.isEmpty())
            {
                skipped.add(new SkippedPokemon(index, speciesLookup.findById(owned.speciesId()).isEmpty()
                    ? "Unknown species" : "No PvP moveset to assume; add it with its moves instead"));
            }
            else
            {
                consider(index, assumed.get(), true, league, eligible, skipped);
            }
        }

        // Stage 1: screen everyone with 1-1 shield 1v1s against the meta, keeping each Pokédex number's best
        // (GO Battle League bans duplicate species), then fully rate the best of those at 0, 1 and 2 shields
        double[][] screen = eligible.parallelStream().map(c -> ratingsAgainst(c, meta, SCREEN_SHIELDS)).toArray(double[][]::new);
        Map<Integer, Integer> bestPerDex = new LinkedHashMap<>();
        for (int c = 0; c < eligible.size(); c++)
        {
            int dex = eligible.get(c).species().dex();
            Integer best = bestPerDex.get(dex);
            if (best == null || average(screen[c]) > average(screen[best]))
            {
                bestPerDex.put(dex, c);
            }
        }
        if (bestPerDex.size() < 3)
        {
            return new TeamBuilderResult(league, meta.size(), metaLookup.snapshotDate(), eligible.size(), List.of(), skipped);
        }
        List<Integer> pool = bestPerDex.values().stream()
            .sorted(Comparator.comparingDouble((Integer c) -> -average(screen[c])).thenComparingInt(c -> c))
            .limit(settings.screenPool())
            .toList();
        double[][] ratings = new double[eligible.size()][];
        double[] metaRating = new double[eligible.size()];
        pool.parallelStream().forEach(c -> ratings[c] = fullRatings(eligible.get(c), meta, screen[c]));
        for (int c : pool)
        {
            metaRating[c] = average(ratings[c]);
        }
        int[] shortlist = pool.stream()
            .sorted(Comparator.comparingDouble((Integer c) -> -metaRating[c]).thenComparingInt(c -> c))
            .limit(settings.shortlistSpecies())
            .mapToInt(Integer::intValue)
            .toArray();

        // Stage 2: every Team of the shortlist, ranked by how well its best member handles each meta Pokémon
        List<Trio> trios = new ArrayList<>();
        for (int a = 0; a < shortlist.length; a++)
        {
            for (int b = a + 1; b < shortlist.length; b++)
            {
                for (int c = b + 1; c < shortlist.length; c++)
                {
                    int[] members = { shortlist[a], shortlist[b], shortlist[c] };
                    trios.add(new Trio(members, coverage(members, ratings, meta.size())));
                }
            }
        }
        List<Trio> finalists = trios.stream()
            .sorted(Comparator.comparingDouble((Trio t) -> -t.coverage()))   // stable: ties keep shortlist order
            .limit(settings.teamsToSimulate())
            .toList();

        // Stage 3: each finalist's best lead against one shared sample of meta Teams
        List<Opponents> selection = sampleOpponents(meta, settings.selectionSamples(), settings.seed());
        List<int[]> orders = new ArrayList<>();
        List<Double> selectionWins = new ArrayList<>();
        for (Trio trio : finalists)
        {
            int[] bestOrder = null;
            double bestWins = -1;
            for (int lead = 0; lead < 3; lead++)
            {
                int[] order = leadFirst(trio.members(), lead);
                double wins = wins(order, eligible, selection);
                if (wins > bestWins)
                {
                    bestWins = wins;
                    bestOrder = order;
                }
            }
            orders.add(bestOrder);
            selectionWins.add(bestWins);
        }
        List<Integer> chosen = IntStream.range(0, finalists.size()).boxed()
            .sorted(Comparator.comparingDouble((Integer t) -> -selectionWins.get(t)))
            .limit(settings.results())
            .toList();

        // Stage 4: odds for the chosen Teams on fresh opponents, so picking the luckiest estimate doesn't inflate them
        List<Opponents> estimate = sampleOpponents(meta, settings.estimateSamples(), settings.seed() + 1);
        List<TeamResult> teams = new ArrayList<>();
        for (int t : chosen)
        {
            int[] order = orders.get(t);
            WinRate odds = WinRate.of(wins(order, eligible, estimate), estimate.size());
            List<TeamMember> members = new ArrayList<>();
            for (int c : order)
            {
                members.add(member(eligible.get(c), metaRating[c]));
            }
            teams.add(new TeamResult(members, odds, toughest(order, ratings, meta)));
        }
        teams.sort(Comparator.comparingDouble((TeamResult team) -> -team.winRate().rate()));
        return new TeamBuilderResult(league, meta.size(), metaLookup.snapshotDate(), eligible.size(), teams, skipped);
    }

    // ---- eligibility ----

    String problemWith(CaughtPokemonRequest request, League league)
    {
        Optional<SpeciesType> found = speciesLookup.findById(request.speciesId());
        if (found.isEmpty())
        {
            return "Unknown species";
        }
        SpeciesType species = found.get();
        // PvPoke tags these "mega"; our species data keeps them (ADR 0003) but GBL's open Leagues don't allow them
        if (species.speciesId().contains("_mega") || species.speciesId().endsWith("_primal"))
        {
            return "Megas can't battle in GO Battle League";
        }
        if (!species.learnset().allows(request.fastMove(), request.chargedMoves()))
        {
            return "Can't know that moveset";
        }
        for (String id : request.chargedMoves())
        {
            Move move = moveLookup.findById(id).orElseThrow();
            if ("both".equals(move.buffTarget()))   // BattleMove rejects these until PvPoke's two-sided buffs are ported
            {
                return move.name() + " isn't simulated yet";
            }
        }
        if (request.shadow() && !species.shadowEligible())
        {
            return "Can't be a Shadow";
        }
        if (league.buildLevel(species, request.atkIv(), request.defIv(), request.staIv(), request.level()).isEmpty())
        {
            return "Over " + league.cpCap + " CP";
        }
        return null;
    }

    private void consider(int index, CaughtPokemonRequest request, boolean assumed, League league,
                          List<Candidate> eligible, List<SkippedPokemon> skipped)
    {
        String problem = problemWith(request, league);
        if (problem != null)
        {
            skipped.add(new SkippedPokemon(index, problem));
        }
        else
        {
            eligible.add(candidate(index, request, assumed, league));
        }
    }

    Candidate candidate(int index, CaughtPokemonRequest request, boolean assumed, League league)
    {
        SpeciesType species = speciesLookup.findById(request.speciesId()).orElseThrow();
        OptionalDouble level = league.buildLevel(species, request.atkIv(), request.defIv(), request.staIv(), request.level());
        Combatant combatant = new Combatant(species, request.atkIv(), request.defIv(), request.staIv(), level.orElseThrow(), request.shadow());
        List<Move> charged = request.chargedMoves().stream().map(id -> moveLookup.findById(id).orElseThrow()).toList();
        Moveset moveset = new Moveset(moveLookup.findById(request.fastMove()).orElseThrow(), charged);
        int cp = League.cp(species, request.atkIv(), request.defIv(), request.staIv(), level.orElseThrow());
        return new Candidate(index, request, assumed, species, combatant, moveset, cp);
    }

    // ---- 1v1 screening ----

    /** Battle rating against each meta Pokémon with both sides on {@code shields}. */
    private static double[] ratingsAgainst(Candidate candidate, List<MetaPokemon> meta, int shields)
    {
        double[] ratings = new double[meta.size()];
        for (int m = 0; m < meta.size(); m++)
        {
            ratings[m] = Battle.simulate(candidate.combatant(), candidate.moveset(), meta.get(m).combatant(), meta.get(m).moveset(),
                new ShieldScenario(shields, shields)).ratingA();
        }
        return ratings;
    }

    /** Ratings averaged over every even shield scenario, reusing the screen's 1-1 battles. */
    private static double[] fullRatings(Candidate candidate, List<MetaPokemon> meta, double[] screened)
    {
        double[] total = screened.clone();
        for (int shields : SHIELDS)
        {
            if (shields != SCREEN_SHIELDS)
            {
                double[] more = ratingsAgainst(candidate, meta, shields);
                for (int m = 0; m < total.length; m++)
                {
                    total[m] += more[m];
                }
            }
        }
        for (int m = 0; m < total.length; m++)
        {
            total[m] /= SHIELDS.length;
        }
        return total;
    }

    /** For each meta Pokémon, the best rating any member gets against it; averaged. */
    private static double coverage(int[] members, double[][] ratings, int metaSize)
    {
        double total = 0;
        for (int m = 0; m < metaSize; m++)
        {
            total += bestAgainst(members, ratings, m);
        }
        return total / metaSize;
    }

    private static double bestAgainst(int[] members, double[][] ratings, int m)
    {
        double best = 0;
        for (int c : members)
        {
            best = Math.max(best, ratings[c][m]);
        }
        return best;
    }

    private static List<String> toughest(int[] members, double[][] ratings, List<MetaPokemon> meta)
    {
        return IntStream.range(0, meta.size()).boxed()
            .sorted(Comparator.comparingDouble((Integer m) -> bestAgainst(members, ratings, m)))
            .limit(TOUGHEST_SHOWN)
            .map(m -> meta.get(m).id())
            .toList();
    }

    // ---- 3v3 sampling ----

    /** Random meta Teams of three different species, lead first; the same seed always gives the same Teams. */
    private static List<Opponents> sampleOpponents(List<MetaPokemon> meta, int count, long seed)
    {
        Random random = new Random(seed);
        List<Opponents> teams = new ArrayList<>();
        for (int t = 0; t < count; t++)
        {
            Set<Integer> dexes = new HashSet<>();
            List<Combatant> combatants = new ArrayList<>();
            List<Moveset> movesets = new ArrayList<>();
            while (combatants.size() < 3)
            {
                MetaPokemon pick = meta.get(random.nextInt(meta.size()));
                if (dexes.add(pick.combatant().species().dex()))   // no Shadow and regular of one species together
                {
                    combatants.add(pick.combatant());
                    movesets.add(pick.moveset());
                }
            }
            teams.add(new Opponents(List.copyOf(combatants), List.copyOf(movesets)));
        }
        return teams;
    }

    /** Wins against each opposing Team, ties as half. */
    private static double wins(int[] order, List<Candidate> eligible, List<Opponents> opponents)
    {
        List<Combatant> combatants = new ArrayList<>();
        List<Moveset> movesets = new ArrayList<>();
        for (int c : order)
        {
            combatants.add(eligible.get(c).combatant());
            movesets.add(eligible.get(c).moveset());
        }
        return opponents.parallelStream()
            .mapToDouble(opponent -> {
                Winner winner = TeamBattle.simulate(combatants, movesets, opponent.combatants(), opponent.movesets()).winner();
                return winner == Winner.A ? 1 : winner == Winner.TIE ? 0.5 : 0;
            })
            .sum();
    }

    private static int[] leadFirst(int[] members, int lead)
    {
        int[] order = new int[3];
        order[0] = members[lead];
        int next = 1;
        for (int i = 0; i < 3; i++)
        {
            if (i != lead)
            {
                order[next++] = members[i];
            }
        }
        return order;
    }

    private static TeamMember member(Candidate candidate, double metaRating)
    {
        CaughtPokemonRequest request = candidate.request();
        return new TeamMember(candidate.index(), request.speciesId(), request.shadow(), candidate.combatant().level(), candidate.cp(),
            request.atkIv(), request.defIv(), request.staIv(), request.fastMove(), request.chargedMoves(),
            Math.round(metaRating * 10) / 10.0, candidate.assumed());
    }

    private static double average(double[] values)
    {
        double total = 0;
        for (double value : values)
        {
            total += value;
        }
        return total / values.length;
    }
}

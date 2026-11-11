package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Battle;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Combatant;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Moveset;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.ShieldScenario;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.TeamBattle;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Winner;
import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.PowerUpCost;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.Cup;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.CupEntrant;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

/**
 * Finds the best Teams of three from a set of Pokémon, ranked by sampled 3v3 win odds (ADR 0005):
 * screen with 1v1s against the meta, shortlist Teams by coverage, pick each one's lineup on a selection sample,
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
        static final Settings DEFAULT = new Settings(30, 10, 8, 100, 400, 5, 2026);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TeamBuilder.class);
    private static final int SCREEN_SHIELDS = 1;
    private static final int[] SHIELDS = { 0, 1, 2 };
    private static final int TOUGHEST_SHOWN = 3;
    private static final int THREATS_SHOWN = 5;

    /**
     * An eligible Pokémon from the request, built for the League. Shared with IvComparison. {@code evolution} is set
     * when it's one of the request's Pokémon tried as a form it can evolve into, null otherwise.
     */
    record Candidate(int index, CaughtPokemonRequest request, boolean assumed, SpeciesType species, Combatant combatant, Moveset moveset, int cp,
                     Evolution evolution) {}

    /** A Team being considered: three shortlist positions, and its 1v1 coverage of the meta. */
    private record Trio(int[] members, double coverage) {}

    /** One finalist Team in one lineup: its order and its wins so far against the selection sample. */
    record Trial(int finalist, int[] order, double wins) {}

    /**
     * The trials, best first, that go on to play the rest of the selection sample: the best few, plus the best trial of
     * further Teams until {@code results} different Teams are in, so one Team's six lineups can't crowd the others out.
     */
    static List<Trial> survivors(List<Trial> ranked, int results)
    {
        List<Trial> survivors = new ArrayList<>();
        Set<Integer> represented = new HashSet<>();
        for (Trial trial : ranked)
        {
            boolean newTeam = !represented.contains(trial.finalist());
            if (survivors.size() < results + SURVIVORS_EXTRA || (newTeam && represented.size() < results))
            {
                survivors.add(trial);
                represented.add(trial.finalist());
            }
        }
        return survivors;
    }

    /** How many (Team, lineup) trials beyond the Teams shown play the whole selection sample. */
    private static final int SURVIVORS_EXTRA = 2;

    /** An opposing Team, lead first. */
    private record Opponents(List<Combatant> combatants, List<Moveset> movesets) {}

    private final SpeciesLookup speciesLookup;
    private final MoveLookup moveLookup;
    private final MetaLookup metaLookup;
    private final GoodIvs goodIvs;
    private final Settings settings;
    /** Each meta against itself, for suggesting replacements without simulating (#112). */
    private final MetaMatrices matrices = new MetaMatrices();

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

    /** Teams for a Cup: its meta, and only the Pokémon its rules let in. */
    public TeamBuilderResult build(Cup cup, List<CaughtPokemonRequest> pokemon, List<OwnedSpecies> species)
    {
        return build(cup, pokemon, species, List.of());
    }

    /** As {@link #build(Cup, List, List)}, also trying {@code evolved}: the Trainer's Pokémon as forms they evolve into. */
    public TeamBuilderResult build(Cup cup, List<CaughtPokemonRequest> pokemon, List<OwnedSpecies> species, List<EvolvedPokemon> evolved)
    {
        return search(cup.league(), cup.meta(), cup.updated(), cup, pokemon, species, evolved);
    }

    /**
     * Teams from the Trainer's exact Pokémon plus the species they ticked on the checklist. Indexes in the result
     * count {@code pokemon} first, then {@code species}: species {@code s} is index {@code pokemon.size() + s}.
     */
    public TeamBuilderResult build(League league, List<CaughtPokemonRequest> pokemon, List<OwnedSpecies> species)
    {
        return build(league, pokemon, species, List.of());
    }

    /**
     * As {@link #build(League, List, List)}, also trying {@code evolved}: the Trainer's Pokémon as forms they evolve
     * into (#138). An evolved form keeps its original's index, and a Team never holds two forms of one Pokémon.
     */
    public TeamBuilderResult build(League league, List<CaughtPokemonRequest> pokemon, List<OwnedSpecies> species, List<EvolvedPokemon> evolved)
    {
        return search(league, metaLookup.meta(league), metaLookup.snapshotDate(), null, pokemon, species, evolved);
    }

    /** {@code cup} is null for the open League. */
    private TeamBuilderResult search(League league, List<MetaPokemon> meta, String metaDate, Cup cup,
                                     List<CaughtPokemonRequest> pokemon, List<OwnedSpecies> species, List<EvolvedPokemon> evolved)
    {
        long started = System.nanoTime();
        TeamBuilderResult result = searchTeams(league, meta, metaDate, cup, pokemon, species, evolved);
        log.info("Team Builder: {} Pokémon, {} eligible, {} vs {} meta: {} ms", pokemon.size() + species.size(), result.eligible(),
            cup == null ? league : cup.key(), meta.size(), (System.nanoTime() - started) / 1_000_000);
        return result;
    }

    private TeamBuilderResult searchTeams(League league, List<MetaPokemon> meta, String metaDate, Cup cup,
                                          List<CaughtPokemonRequest> pokemon, List<OwnedSpecies> species, List<EvolvedPokemon> evolved)
    {
        List<SkippedPokemon> skipped = new ArrayList<>();
        List<Candidate> eligible = new ArrayList<>();
        for (int i = 0; i < pokemon.size(); i++)
        {
            consider(i, pokemon.get(i), false, league, cup, eligible, skipped);
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
                consider(index, assumed.get(), true, league, cup, eligible, skipped);
            }
        }

        // the Trainer's Pokémon as what they'd evolve into (#138); a form that can't battle here just isn't tried
        for (EvolvedPokemon form : evolved)
        {
            if (problemWith(form.pokemon(), league, cup) == null)
            {
                eligible.add(candidate(form.index(), form.pokemon(), false, league, form.evolution()));
            }
        }

        // Stage 1: screen everyone with 1-1 shield 1v1s against the meta, keeping each Pokédex number's best
        // (GO Battle League bans duplicate species), then fully rate the best of those at 0, 1 and 2 shields
        int battleCp = cup == null ? league.battleCp() : cup.cpCap();
        double[][] screen = eligible.parallelStream().map(c -> ratingsAgainst(c, meta, SCREEN_SHIELDS, battleCp)).toArray(double[][]::new);
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
            return new TeamBuilderResult(league, meta.size(), metaDate, eligible.size(), List.of(), skipped, cup == null ? null : cup.key());
        }
        List<Integer> pool = bestPerDex.values().stream()
            .sorted(Comparator.comparingDouble((Integer c) -> -average(screen[c])).thenComparingInt(c -> c))
            .limit(settings.screenPool())
            .toList();
        double[][] ratings = new double[eligible.size()][];
        double[] metaRating = new double[eligible.size()];
        pool.parallelStream().forEach(c -> ratings[c] = fullRatings(eligible.get(c), meta, screen[c], battleCp));
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
                    if (onePokemonTwice(members, eligible))
                    {
                        continue;   // a Pokémon and its own evolution (#138)
                    }
                    trios.add(new Trio(members, coverage(members, ratings, meta.size())));
                }
            }
        }
        List<Trio> finalists = trios.stream()
            .sorted(Comparator.comparingDouble((Trio t) -> -t.coverage()))   // stable: ties keep shortlist order
            .limit(settings.teamsToSimulate())
            .toList();

        // Stage 3: each finalist's best lineup (lead, safe swap, closer) against one shared sample of meta Teams.
        // Every Team and lineup plays the first sixth of the sample; only the most promising go on to play the rest,
        // so a clearly worse lineup or Team doesn't cost a full sample of 3v3s
        List<Opponents> selection = sampleOpponents(meta, settings.selectionSamples(), settings.seed());
        int firstRound = Math.max(1, selection.size() / 6);
        List<Opponents> early = selection.subList(0, firstRound);
        List<Opponents> rest = selection.subList(firstRound, selection.size());
        List<Trial> trials = new ArrayList<>();
        for (int f = 0; f < finalists.size(); f++)
        {
            for (int[] order : lineups(finalists.get(f).members()))
            {
                trials.add(new Trial(f, order, wins(order, eligible, early, battleCp)));
            }
        }
        trials.sort(Comparator.comparingDouble((Trial t) -> -t.wins()));   // stable: ties keep finalist, then lineup order
        List<Trial> survivors = new ArrayList<>();
        for (Trial trial : survivors(trials, settings.results()))
        {
            survivors.add(new Trial(trial.finalist(), trial.order(), trial.wins() + wins(trial.order(), eligible, rest, battleCp)));
        }
        Map<Integer, Trial> bestPerTeam = new LinkedHashMap<>();
        for (Trial trial : survivors)
        {
            bestPerTeam.merge(trial.finalist(), trial, (a, b) -> b.wins() > a.wins() ? b : a);
        }
        List<int[]> chosen = bestPerTeam.values().stream()
            .sorted(Comparator.comparingDouble((Trial t) -> -t.wins()).thenComparingInt(Trial::finalist))
            .limit(settings.results())
            .map(Trial::order)
            .toList();

        // Stage 4: odds for the chosen Teams on fresh opponents, so picking the luckiest estimate doesn't inflate them
        List<Opponents> estimate = sampleOpponents(meta, settings.estimateSamples(), settings.seed() + 1);
        List<TeamResult> teams = new ArrayList<>();
        String matrixKey = cup == null ? league.name() : cup.key() + "@" + cup.updated();
        Map<Integer, PowerUpImpact> powerUps = new HashMap<>();   // a Pokémon on several Teams is battled once
        for (int[] order : chosen)
        {
            WinRate odds = WinRate.of(wins(order, eligible, estimate, battleCp), estimate.size());
            List<TeamMember> members = new ArrayList<>();
            for (int c : order)
            {
                PowerUpImpact powerUp = powerUps.computeIfAbsent(c, k -> powerUp(eligible.get(k), meta, battleCp).orElse(null));
                members.add(member(eligible.get(c), metaRating[c], powerUp));
            }
            List<Suggestion> suggestions = List.of();
            if (Replacements.needed(odds))
            {
                List<Replacements.Member> team = new ArrayList<>();
                for (int c : order)
                {
                    Candidate candidate = eligible.get(c);
                    team.add(new Replacements.Member(candidate.index(), candidate.request().speciesId(), candidate.species().dex(), ratings[c]));
                }
                suggestions = Replacements.suggest(matrices.get(matrixKey, meta, battleCp), team);
            }
            teams.add(new TeamResult(members, odds, toughest(order, ratings, meta), suggestions, teamCoverage(order, ratings, meta.size()),
                threats(order, ratings, meta, eligible)));
        }
        teams.sort(Comparator.comparingDouble((TeamResult team) -> -team.winRate().rate()));
        return new TeamBuilderResult(league, meta.size(), metaDate, eligible.size(), teams, skipped, cup == null ? null : cup.key());
    }

    /** Whether two of these candidates are the same Pokémon from the request, as different forms it can evolve into. */
    private static boolean onePokemonTwice(int[] members, List<Candidate> eligible)
    {
        Set<Integer> indexes = new HashSet<>();
        for (int c : members)
        {
            if (!indexes.add(eligible.get(c).index()))
            {
                return true;
            }
        }
        return false;
    }

    /** Works out the open Leagues' meta matrices in the background at startup, so no search waits on them. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmMetaMatrices()
    {
        Thread.ofVirtual().name("meta-matrices").start(() -> {
            for (League league : League.values())
            {
                matrices.get(league.name(), metaLookup.meta(league), league.battleCp());
            }
        });
    }

    // ---- eligibility ----

    String problemWith(CaughtPokemonRequest request, League league)
    {
        return problemWith(request, league, null);
    }

    /** Why this Pokémon can't battle here, or null. In a Cup, the Cup's own rules decide who may enter, Megas included. */
    String problemWith(CaughtPokemonRequest request, League league, Cup cup)
    {
        Optional<SpeciesType> found = speciesLookup.findById(request.speciesId());
        if (found.isEmpty())
        {
            return "Unknown species";
        }
        SpeciesType species = found.get();
        // PvPoke tags these "mega"; our species data keeps them (ADR 0003) but GBL's open Leagues don't allow them
        if (cup != null)
        {
            CupEntrant entrant = CupEntrant.of(species, speciesLookup.traits(species.speciesId()).orElseThrow(), request.shadow());
            if (!cup.eligibility().allows(entrant, cup.cpCap()))
            {
                return "Not allowed in " + cup.title();
            }
        }
        else if (species.speciesId().contains("_mega") || species.speciesId().endsWith("_primal"))
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
        if (league.buildLevel(species, request.atkIv(), request.defIv(), request.staIv(), currentLevel(request)).isEmpty())
        {
            return "Over " + league.cpCap + " CP";
        }
        return null;
    }

    private void consider(int index, CaughtPokemonRequest request, boolean assumed, League league, Cup cup,
                          List<Candidate> eligible, List<SkippedPokemon> skipped)
    {
        String problem = problemWith(request, league, cup);
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
        return candidate(index, request, assumed, league, null);
    }

    private Candidate candidate(int index, CaughtPokemonRequest request, boolean assumed, League league, Evolution evolution)
    {
        SpeciesType species = speciesLookup.findById(request.speciesId()).orElseThrow();
        OptionalDouble level = league.buildLevel(species, request.atkIv(), request.defIv(), request.staIv(), currentLevel(request));
        Combatant combatant = new Combatant(species, request.atkIv(), request.defIv(), request.staIv(), level.orElseThrow(), request.shadow());
        List<Move> charged = request.chargedMoves().stream().map(id -> moveLookup.findById(id).orElseThrow()).toList();
        Moveset moveset = new Moveset(moveLookup.findById(request.fastMove()).orElseThrow(), charged);
        int cp = League.cp(species, request.atkIv(), request.defIv(), request.staIv(), level.orElseThrow());
        return new Candidate(index, request, assumed, species, combatant, moveset, cp, evolution);
    }

    // ---- 1v1 screening ----

    /** Battle rating against each meta Pokémon with both sides on {@code shields}. */
    private static double[] ratingsAgainst(Candidate candidate, List<MetaPokemon> meta, int shields, int battleCp)
    {
        double[] ratings = new double[meta.size()];
        for (int m = 0; m < meta.size(); m++)
        {
            ratings[m] = Battle.simulate(candidate.combatant(), candidate.moveset(), meta.get(m).combatant(), meta.get(m).moveset(),
                new ShieldScenario(shields, shields), battleCp).ratingA();
        }
        return ratings;
    }

    /** Ratings averaged over every even shield scenario, reusing the screen's 1-1 battles. */
    private static double[] fullRatings(Candidate candidate, List<MetaPokemon> meta, double[] screened, int battleCp)
    {
        double[] total = screened.clone();
        for (int shields : SHIELDS)
        {
            if (shields != SCREEN_SHIELDS)
            {
                double[] more = ratingsAgainst(candidate, meta, shields, battleCp);
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
        return worstFirst(members, ratings, meta.size()).limit(TOUGHEST_SHOWN).map(m -> meta.get(m).id()).toList();
    }

    /** Meta indexes, the one the Team's best answer does worst against first; ties in meta order. */
    private static java.util.stream.Stream<Integer> worstFirst(int[] members, double[][] ratings, int metaSize)
    {
        return IntStream.range(0, metaSize).boxed().sorted(Comparator.comparingDouble((Integer m) -> bestAgainst(members, ratings, m)));
    }

    /** The meta coverage the shortlist ranks on, and how many meta Pokémon some member beats (#113). */
    private static TeamCoverage teamCoverage(int[] members, double[][] ratings, int metaSize)
    {
        int answered = 0;
        for (int m = 0; m < metaSize; m++)
        {
            answered += bestAgainst(members, ratings, m) > 500 ? 1 : 0;
        }
        return new TeamCoverage(Math.round(coverage(members, ratings, metaSize) * 10) / 10.0, answered, metaSize);
    }

    /** The 5 meta Pokémon the Team does worst against, with its best rating against each and who gets it (#113). */
    private static List<Threat> threats(int[] members, double[][] ratings, List<MetaPokemon> meta, List<Candidate> eligible)
    {
        return worstFirst(members, ratings, meta.size()).limit(THREATS_SHOWN)
            .map(m -> {
                int answer = members[0];
                for (int c : members)
                {
                    if (ratings[c][m] > ratings[answer][m]) answer = c;
                }
                return new Threat(meta.get(m).id(), Math.round(ratings[answer][m] * 10) / 10.0,
                    eligible.get(answer).request().speciesId());
            })
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

    /** Wins against each opposing Team, ties as half. Our side plays its lineup's roles; the meta side plays as PvPoke's AI. */
    private static double wins(int[] order, List<Candidate> eligible, List<Opponents> opponents, int battleCp)
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
                Winner winner = TeamBattle.simulateWithRoles(combatants, movesets, opponent.combatants(), opponent.movesets(), battleCp).winner();
                return winner == Winner.A ? 1 : winner == Winner.TIE ? 0.5 : 0;
            })
            .sum();
    }

    /** Every lineup of a Team: lead, safe swap (who a switch-out goes to), closer. */
    static List<int[]> lineups(int[] members)
    {
        List<int[]> lineups = new ArrayList<>();
        for (int lead = 0; lead < 3; lead++)
        {
            for (int swap = 0; swap < 3; swap++)
            {
                if (swap != lead)
                {
                    lineups.add(new int[] { members[lead], members[swap], members[3 - lead - swap] });
                }
            }
        }
        return lineups;
    }

    /**
     * Its meta battles at its current level against those at the level it's built at. Empty for a checklist tick
     * or one added without a CP (no current level to compare), or one already at its built level.
     */
    private static Optional<PowerUpImpact> powerUp(Candidate candidate, List<MetaPokemon> meta, int battleCp)
    {
        Double current = candidate.request().level();
        Combatant built = candidate.combatant();
        if (candidate.assumed() || current == null || current >= built.level())
        {
            return Optional.empty();
        }
        Combatant now = new Combatant(built.species(), built.atkIv(), built.defIv(), built.staIv(), current, built.shadow());
        MetaBattles.Record before = MetaBattles.against(now, candidate.moveset(), meta, battleCp);
        MetaBattles.Record after = MetaBattles.against(built, candidate.moveset(), meta, battleCp);
        List<MatchupRef> gained = new ArrayList<>();
        List<MatchupRef> lost = new ArrayList<>();
        for (int b = 0; b < before.won().length; b++)
        {
            if (after.won()[b] && !before.won()[b]) gained.add(MetaBattles.matchup(meta, b));
            if (before.won()[b] && !after.won()[b]) lost.add(MetaBattles.matchup(meta, b));
        }
        int fromCp = League.cp(candidate.species(), built.atkIv(), built.defIv(), built.staIv(), current);
        return Optional.of(new PowerUpImpact(current, fromCp, before.wins(), after.wins(), before.won().length,
            List.copyOf(gained), List.copyOf(lost)));
    }

    /** Current level to built level; null for a checklist tick or one added without a CP. */
    private static PowerUpCost.Cost cost(Candidate candidate)
    {
        Double current = candidate.request().level();
        if (candidate.assumed() || current == null)
        {
            return null;
        }
        return PowerUpCost.between(current, candidate.combatant().level(), candidate.request().shadow());
    }

    private static TeamMember member(Candidate candidate, double metaRating, PowerUpImpact powerUp)
    {
        CaughtPokemonRequest request = candidate.request();
        return new TeamMember(candidate.index(), request.speciesId(), request.shadow(), candidate.combatant().level(), candidate.cp(),
            request.atkIv(), request.defIv(), request.staIv(), request.fastMove(), request.chargedMoves(),
            Math.round(metaRating * 10) / 10.0, candidate.assumed(), powerUp, cost(candidate), candidate.evolution());
    }

    /**
     * A Pokémon added without its CP has no known level. It's taken as able to reach the League's best level,
     * so it's never ruled out as over the Cap; typing its CP makes that check exact.
     */
    static double currentLevel(CaughtPokemonRequest request)
    {
        return request.level() == null ? 1.0 : request.level();
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

package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.CpCalculator;
import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.RankedIvResult;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

/**
 * What an unevolved Pokémon becomes: for each later stage and each League, the level it would be powered up to (the
 * highest under the cap, never below its level now, since evolving keeps the level), its CP, and how its stat
 * product ranks against all 4,096 spreads of that form. A form already over a League's cap at its level is marked
 * ineligible: levels can't go down. Shadow doesn't change CP or stat product, so it's only carried along.
 */
@Service
public class EvolutionProjector
{
    /** Levels past 40 cost XL Candy. */
    private static final double XL_FROM_LEVEL = 40;
    private static final double BEST_BUDDY_LEVEL = 51;

    private final SpeciesLookup species;
    private final EvolutionTree tree;
    private final EvolutionMoves moves;
    /** Each form's 4,096 ranked spreads per League, worked out once. */
    private final Map<String, RankedIvResult[]> rankings = new ConcurrentHashMap<>();

    public EvolutionProjector(SpeciesLookup species, EvolutionTree tree, EvolutionMoves moves)
    {
        this.species = species;
        this.tree = tree;
        this.moves = moves;
    }

    /** Empty for an unknown species. {@code level} is null when it isn't known; it's then projected from level 1. */
    public Optional<EvolutionProjection> find(String speciesId, int atkIv, int defIv, int staIv, Double level, boolean shadow)
    {
        return species.findById(speciesId).map(base -> {
            double current = level == null ? 1.0 : level;
            List<FormProjection> forms = new ArrayList<>();
            for (EvolvedForm form : tree.evolutionsOf(speciesId))
            {
                SpeciesType evolved = species.findById(form.speciesId()).orElseThrow();
                List<LeagueProjection> leagues = new ArrayList<>();
                for (League league : League.values())
                {
                    leagues.add(project(evolved, league, atkIv, defIv, staIv, current));
                }
                forms.add(new FormProjection(evolved.speciesId(), evolved.speciesName(), evolved.dex(), form.stage(), form.path(),
                    List.copyOf(leagues), moves.forForm(evolved.speciesId()).orElseThrow()));
            }
            return new EvolutionProjection(base.speciesId(), base.speciesName(), shadow, List.copyOf(forms));
        });
    }

    public EvolutionProjection project(String speciesId, int atkIv, int defIv, int staIv, Double level, boolean shadow)
    {
        return find(speciesId, atkIv, defIv, staIv, level, shadow).orElseThrow();
    }

    private LeagueProjection project(SpeciesType s, League league, int atkIv, int defIv, int staIv, double current)
    {
        OptionalDouble built = league.buildLevel(s, atkIv, defIv, staIv, current);
        if (built.isEmpty())
        {
            return new LeagueProjection(league, false, null, null, null, null, null, false, null);
        }
        double level = built.getAsDouble();
        int cp = CpCalculator.calculateCP(s.baseAtk(), s.baseDef(), s.baseSta(), atkIv, defIv, staIv, level);
        double statProduct = CpCalculator.calculateStatProduct(s.baseAtk(), s.baseDef(), s.baseSta(), atkIv, defIv, staIv, level)
            .statProduct();
        RankedIvResult[] ranked = rankings.computeIfAbsent(s.speciesId() + "@" + league,
            key -> CpCalculator.rankAllCombos(s.baseAtk(), s.baseDef(), s.baseSta(), league.cap() != 0, league.cap(), false));
        int rank = ranked[atkIv * 256 + defIv * 16 + staIv].statProductRank();
        double best = 0;
        for (RankedIvResult r : ranked)
        {
            if (r.statProductRank() == 1)
            {
                best = r.ivResult().statProduct();
            }
        }
        double percent = Math.round(statProduct / best * 10000) / 100.0;
        Integer bestBuddyCp = league.cap() == 0
            ? CpCalculator.calculateCP(s.baseAtk(), s.baseDef(), s.baseSta(), atkIv, defIv, staIv, BEST_BUDDY_LEVEL)
            : null;
        return new LeagueProjection(league, true, level, cp, statProduct, rank, percent, level > XL_FROM_LEVEL, bestBuddyCp);
    }
}

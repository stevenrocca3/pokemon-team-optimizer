package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.CpCalculator;
import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.IvResult;
import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.RankedIvResult;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMoves;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMovesets;

/**
 * Stands in for a checklist species the Trainer owns but hasn't entered exactly: the spread at the top-10% line
 * of stat product for the League (rank 410 of 4096, a good one a PvP player would keep), PvPoke's suggested
 * moveset, and level 1 so the Team Builder powers it to the League's best level like any other Pokémon.
 */
@Component
public class GoodIvs
{
    /** 1-based stat product rank assumed: the worst spread still in the top 10% of 4096. */
    static final int ASSUMED_RANK = 410;

    private final SpeciesLookup speciesLookup;
    private final SuggestedMovesets movesets;
    /** rankAllCombos is 4096 CP searches; a species' spread per League never changes, so it's worked out once */
    private final Map<String, IvResult> spreadCache = new ConcurrentHashMap<>();

    public GoodIvs(SpeciesLookup speciesLookup, SuggestedMovesets movesets)
    {
        this.speciesLookup = speciesLookup;
        this.movesets = movesets;
    }

    /** Empty for an unknown species, or one with no suggested moveset to battle with. */
    public Optional<CaughtPokemonRequest> assume(OwnedSpecies owned, League league)
    {
        Optional<SpeciesType> species = speciesLookup.findById(owned.speciesId());
        Optional<SuggestedMoves> moves = movesets.forSpecies(owned.speciesId());
        if (species.isEmpty() || moves.isEmpty())
        {
            return Optional.empty();
        }
        IvResult ivs = spreadCache.computeIfAbsent(species.get().speciesId() + "/" + league, key -> spread(species.get(), league));
        return Optional.of(new CaughtPokemonRequest(owned.speciesId(), ivs.atkIV(), ivs.defIV(), ivs.staIV(), 1.0,
            moves.get().fastMove(), moves.get().chargedMoves(), owned.shadow()));
    }

    private static IvResult spread(SpeciesType s, League league)
    {
        return Arrays.stream(CpCalculator.rankAllCombos(s.baseAtk(), s.baseDef(), s.baseSta(), league.cpCap != 0, league.cpCap, false))
            .filter(r -> r.statProductRank() == ASSUMED_RANK)
            .map(RankedIvResult::ivResult)
            .findFirst().orElseThrow();
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.CpCalculator;
import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.RankedIvResult;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMovesets;

public class GoodIvsTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final GoodIvs GOOD_IVS = new GoodIvs(SPECIES, new SuggestedMovesets(SPECIES));

    private static int statProductRank(CaughtPokemonRequest p, League league)
    {
        SpeciesType s = SPECIES.findById(p.speciesId()).orElseThrow();
        RankedIvResult[] all = CpCalculator.rankAllCombos(s.baseAtk(), s.baseDef(), s.baseSta(), league.cpCap != 0, league.cpCap, false);
        return Arrays.stream(all)
            .filter(r -> r.ivResult().atkIV() == p.atkIv() && r.ivResult().defIV() == p.defIv() && r.ivResult().staIV() == p.staIv())
            .findFirst().orElseThrow().statProductRank();
    }

    @Test
    public void assumesTheIvsAtTheTopTenPercentLineForTheLeague()
    {   // 4096 spreads; rank 410 is the worst spread still in the top 10%
        CaughtPokemonRequest great = GOOD_IVS.assume(new OwnedSpecies("medicham", false), League.GREAT).orElseThrow();
        CaughtPokemonRequest ultra = GOOD_IVS.assume(new OwnedSpecies("medicham", false), League.ULTRA).orElseThrow();

        assertEquals(410, statProductRank(great, League.GREAT));
        assertEquals(410, statProductRank(ultra, League.ULTRA));
    }

    @Test
    public void usesTheSuggestedMovesetAndKeepsShadow()
    {
        CaughtPokemonRequest p = GOOD_IVS.assume(new OwnedSpecies("abomasnow", true), League.GREAT).orElseThrow();

        assertEquals("POWDER_SNOW", p.fastMove());
        assertEquals(List.of("WEATHER_BALL_ICE", "ENERGY_BALL"), p.chargedMoves());
        assertTrue(p.shadow());
        assertEquals(1.0, p.level());   // the Team Builder powers it up to the League's best level
    }

    @Test
    public void nothingForAnUnknownSpecies()
    {
        assertTrue(GOOD_IVS.assume(new OwnedSpecies("missingno", false), League.GREAT).isEmpty());
    }
}

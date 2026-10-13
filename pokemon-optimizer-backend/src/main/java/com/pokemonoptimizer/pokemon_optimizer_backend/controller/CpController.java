package com.pokemonoptimizer.pokemon_optimizer_backend.controller;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.CpCalculator;
import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.RankedIvResult;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.RankRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

@RestController
@RequestMapping("/api")
public class CpController 
{

    private final SpeciesLookup speciesLookup;
    public CpController(SpeciesLookup sl)
    {
        speciesLookup = sl;
    }

    // public static RankedIvResult[] rankAllCombos(int baseAtk, int baseDef, int baseSta, boolean hasCap, int cap, boolean hasBestBuddy)
    @PostMapping("/rank")
    public RankedIvResult[] rank(@RequestBody RankRequest request) 
    {
        SpeciesType speciesType = speciesLookup.findById(request.speciesId()).orElseThrow(() 
        -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Species: " + request.speciesId() + " Not Found."));
        return CpCalculator.rankAllCombos(
                    speciesType.baseAtk(), speciesType.baseDef(), speciesType.baseSta(),
                    request.hasCap(), request.cap(), request.hasBestBuddy()
                );
    
        
    }
}
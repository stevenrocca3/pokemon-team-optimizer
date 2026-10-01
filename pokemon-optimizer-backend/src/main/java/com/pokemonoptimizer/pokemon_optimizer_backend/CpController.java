package com.pokemonoptimizer.pokemon_optimizer_backend;

import org.springframework.web.bind.annotation.*; 
import org.springframework.web.server.*;
import org.springframework.http.HttpStatus;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
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
        -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Species: " + request.speciesId() + " Not Found."));
        return CpCalculator.rankAllCombos(
                    speciesType.baseAtk(), speciesType.baseDef(), speciesType.baseSta(),
                    request.hasCap(), request.cap(), request.hasBestBuddy()
                );
    
        
    }
}
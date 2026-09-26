package com.pokemonoptimizer.pokemon_optimizer_backend;

import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api")
public class CpController {

    @PostMapping("/rank")
    public RankedIvResult[] rank(@RequestBody RankRequest request) {
        return CpCalculator.rankAllCombos(
            request.baseAtk(), request.baseDef(), request.baseSta(),
            request.hasCap(), request.cap(), request.hasBestBuddy()
        );
    }
}
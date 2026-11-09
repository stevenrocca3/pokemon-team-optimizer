package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CompareRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.IvComparison;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.IvComparisonResult;

import jakarta.validation.Valid;

/** Public and stateless like the Team Builder: guests send their own copies. A few hundred 1v1s, under a second. */
@RestController
@RequestMapping("/api")
public class CompareController
{
    private final IvComparison comparison;

    public CompareController(IvComparison comparison)
    {
        this.comparison = comparison;
    }

    @PostMapping("/compare")
    public IvComparisonResult compare(@Valid @RequestBody CompareRequest request)
    {
        return comparison.compare(request.league(), request.pokemon());
    }
}

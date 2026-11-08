package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.TeamBuilderRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilder;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilderResult;

import jakarta.validation.Valid;

/**
 * Public and stateless: the Pokémon come in the request, so guests can build Teams without an account
 * and logged-in Trainers send their Collection.
 */
@RestController
@RequestMapping("/api")
public class TeamBuilderController
{
    private final TeamBuilder teamBuilder;

    public TeamBuilderController(TeamBuilder teamBuilder)
    {
        this.teamBuilder = teamBuilder;
    }

    @PostMapping("/team-builder")
    public TeamBuilderResult build(@Valid @RequestBody TeamBuilderRequest request)
    {
        return teamBuilder.build(request.league(), request.pokemon());
    }
}

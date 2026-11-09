package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.server.ResponseStatusException;

import com.pokemonoptimizer.pokemon_optimizer_backend.cups.Cup;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.CupCatalog;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.TeamBuilderRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilder;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilderGate;
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
    private final TeamBuilderGate gate;
    private final CupCatalog cups;

    public TeamBuilderController(TeamBuilder teamBuilder, TeamBuilderGate gate, CupCatalog cups)
    {
        this.teamBuilder = teamBuilder;
        this.gate = gate;
        this.cups = cups;
    }

    @PostMapping("/team-builder")
    public TeamBuilderResult build(@Valid @RequestBody TeamBuilderRequest request)
    {
        if (request.cup() == null)
        {
            // a search takes every core for seconds, so only a few run at once; the rest wait, then get a 503
            return gate.run(() -> teamBuilder.build(request.league(), request.pokemon(), request.species()));
        }
        Cup cup = cups.find(request.cup()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "That Cup isn't running any more. Pick another."));
        if (cup.league() != request.league())
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, cup.title() + " is a " + cup.league() + " League Cup");
        }
        return gate.run(() -> teamBuilder.build(cup, request.pokemon(), request.species()));
    }
}

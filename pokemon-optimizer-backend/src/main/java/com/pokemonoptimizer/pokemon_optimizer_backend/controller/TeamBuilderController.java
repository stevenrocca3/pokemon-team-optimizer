package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import java.util.List;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.server.ResponseStatusException;

import com.pokemonoptimizer.pokemon_optimizer_backend.cups.Cup;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.CupCatalog;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.TeamBuilderRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TeamBuild;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TeamBuildRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilder;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.EvolvedPokemon;
import com.pokemonoptimizer.pokemon_optimizer_backend.evolution.EvolvableCandidates;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilderGate;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.TeamBuilderResult;

import jakarta.validation.Valid;

/**
 * Public and stateless: the Pokémon come in the request, so guests can build Teams without an account
 * and logged-in Trainers send their Collection. Each finished build is counted, anonymously, for the site's totals.
 */
@RestController
@RequestMapping("/api")
public class TeamBuilderController
{
    private final TeamBuilder teamBuilder;
    private final TeamBuilderGate gate;
    private final CupCatalog cups;
    private final TeamBuildRepository teamBuilds;
    private final EvolvableCandidates evolvable;

    public TeamBuilderController(TeamBuilder teamBuilder, TeamBuilderGate gate, CupCatalog cups, TeamBuildRepository teamBuilds,
                                 EvolvableCandidates evolvable)
    {
        this.teamBuilder = teamBuilder;
        this.gate = gate;
        this.cups = cups;
        this.teamBuilds = teamBuilds;
        this.evolvable = evolvable;
    }

    @PostMapping("/team-builder")
    public TeamBuilderResult build(@Valid @RequestBody TeamBuilderRequest request)
    {
        // a search takes every core for seconds, so only a few run at once; the rest wait, then get a 503
        Cup cup = request.cup() == null ? null : runningCup(request);
        List<EvolvedPokemon> evolved = request.includeEvolvable() ? evolvable.expand(request.pokemon()) : List.of();
        TeamBuilderResult result = cup == null
            ? gate.run(() -> teamBuilder.build(request.league(), request.pokemon(), request.species(), evolved))
            : gate.run(() -> teamBuilder.build(cup, request.pokemon(), request.species(), evolved));
        // counted only once it finished: turned-away and failed searches never get here
        teamBuilds.save(new TeamBuild(request.league(), request.cup(), request.pokemon().size() + request.species().size(), Instant.now()));
        return result;
    }

    private Cup runningCup(TeamBuilderRequest request)
    {
        Cup cup = cups.find(request.cup()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "That Cup isn't running any more. Pick another."));
        if (cup.league() != request.league())
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, cup.title() + " is a " + cup.league() + " League Cup");
        }
        return cup;
    }
}

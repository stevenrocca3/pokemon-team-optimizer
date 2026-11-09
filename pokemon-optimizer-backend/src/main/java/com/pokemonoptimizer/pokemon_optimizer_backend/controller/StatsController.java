package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pokemonoptimizer.pokemon_optimizer_backend.checklist.ChecklistRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.RatingSummary;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.SiteStats;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemonRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.RatingRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TeamBuildRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;

/** The site's totals, public on purpose: only counts and the rating split, nothing about any one Trainer. */
@RestController
@RequestMapping("/api/stats")
public class StatsController
{
    private final TeamBuildRepository teamBuilds;
    private final CaughtPokemonRepository caughtPokemon;
    private final ChecklistRepository checklist;
    private final TrainerRepository trainers;
    private final RatingRepository ratings;

    public StatsController(TeamBuildRepository teamBuilds, CaughtPokemonRepository caughtPokemon, ChecklistRepository checklist,
                           TrainerRepository trainers, RatingRepository ratings)
    {
        this.teamBuilds = teamBuilds;
        this.caughtPokemon = caughtPokemon;
        this.checklist = checklist;
        this.trainers = trainers;
        this.ratings = ratings;
    }

    @GetMapping
    public SiteStats stats()
    {
        return new SiteStats(teamBuilds.count(), caughtPokemon.count(), checklist.count(), trainers.count(),
            RatingSummary.from(ratings.countByStars()));
    }
}

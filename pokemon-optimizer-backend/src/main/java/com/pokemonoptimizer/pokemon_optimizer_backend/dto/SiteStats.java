package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

/**
 * Public totals for the home page: counts only, nothing about any one Trainer. Floors, not the whole picture: guests
 * keep their Pokémon in the browser and never make a Trainer.
 */
public record SiteStats(long teamsBuilt, long pokemonSaved, long speciesTicked, long trainers, RatingSummary ratings) {}

package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

import java.util.List;

import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

/** A live Cup for the Team Builder's picker. {@code updated} is the date of PvPoke's data it came from. */
public record CupSummary(String key, String title, League league, int partySize, List<String> rules, int metaSize, String updated) {}

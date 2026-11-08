package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

/**
 * Best Teams first. Empty {@code teams} when fewer than 3 different species can play in the League.
 * {@code metaDate} is the PvPoke snapshot the meta came from.
 */
public record TeamBuilderResult(League league, int metaSize, String metaDate, int eligible, List<TeamResult> teams,
                                List<SkippedPokemon> skipped) {}

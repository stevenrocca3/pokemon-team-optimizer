package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

/**
 * What powering a Pokémon from its current level to the one it's built at changes: meta battles won (at 0, 1 and
 * 2 shields) before and after, and exactly which ones flip.
 */
public record PowerUpImpact(double fromLevel, int fromCp, int winsBefore, int winsAfter, int battles,
                            List<MatchupRef> gained, List<MatchupRef> lost) {}

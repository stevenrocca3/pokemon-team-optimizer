package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

/**
 * A Pokémon built for a League against each meta Pokémon. Stats are in-battle values (Shadow included);
 * {@code maxAttack}/{@code maxDefense} are the highest any IV spread of the species reaches under the Cap.
 */
public record BreakpointReport(League league, double level, int cp, String fastMove, double attack, double defense,
                               double maxAttack, double maxDefense, List<OpponentBreakpoint> opponents) {}

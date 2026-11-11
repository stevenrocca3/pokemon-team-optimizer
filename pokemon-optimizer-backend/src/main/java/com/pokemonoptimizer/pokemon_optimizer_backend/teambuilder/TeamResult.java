package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

/**
 * A recommended Team. {@code members} are in battle order: the lead first. {@code winRate} comes from 3v3 battles
 * against freshly sampled meta Teams. {@code toughest} are the meta Pokémon (PvPoke IDs) the Team's best 1v1 answer
 * does worst against. {@code suggestions} are 5 meta Pokémon to swap in when it wins under half its battles (#112),
 * best first; empty otherwise. {@code coverage} is its meta coverage and {@code threats} the 5 meta Pokémon it does
 * worst against, worst first (#113).
 */
public record TeamResult(List<TeamMember> members, WinRate winRate, List<String> toughest, List<Suggestion> suggestions,
                         TeamCoverage coverage, List<Threat> threats) {}

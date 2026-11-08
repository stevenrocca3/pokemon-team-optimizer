package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

/**
 * A recommended Team. {@code members} are in battle order: the lead first. {@code winRate} comes from 3v3 battles
 * against freshly sampled meta Teams. {@code toughest} are the meta Pokémon (PvPoke IDs) the Team's best 1v1 answer
 * does worst against.
 */
public record TeamResult(List<TeamMember> members, WinRate winRate, List<String> toughest) {}

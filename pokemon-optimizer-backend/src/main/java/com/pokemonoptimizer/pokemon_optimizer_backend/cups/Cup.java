package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.util.List;

import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.MetaPokemon;

/** A live Cup, ready to build Teams for: its League's CP cap, entry rules and meta. {@code updated} is PvPoke's date. */
public record Cup(String key, String title, League league, int cpCap, int partySize, List<String> rules, CupRules eligibility,
                  List<MetaPokemon> meta, String updated) {}

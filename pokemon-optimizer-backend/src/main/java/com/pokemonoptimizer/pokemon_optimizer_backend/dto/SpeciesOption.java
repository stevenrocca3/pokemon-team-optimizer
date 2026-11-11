package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

/** {@code evolves}: it has a released form to evolve into, so the Collection can show its evolution line (#140). */
public record SpeciesOption (String id, String name, int dex, boolean shadowEligible, boolean evolves){}

package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

/** How many ratings gave this many stars. Boxed to match what the JPQL query hands the constructor. */
public record StarCount(Integer stars, Long count) {}

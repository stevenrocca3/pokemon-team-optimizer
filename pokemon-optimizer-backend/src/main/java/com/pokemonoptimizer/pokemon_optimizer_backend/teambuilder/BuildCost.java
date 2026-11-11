package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.PowerUpCost;

/**
 * What evolving one of the Trainer's Pokémon takes, beyond powering it up (#139). {@code family} is its Candy family
 * (PvPoke's "FAMILY_MUDKIP"), null when unknown. {@code steps} are the evolutions in order; {@code complete} is false
 * when the game data has no cost for one of them, and {@code evolveCandy} then counts only the known ones.
 * {@code secondMove} unlocks its second Charged Move (1.2x for a Shadow); null when its best moveset has one, or the
 * cost isn't known.
 */
public record BuildCost(String family, List<EvolutionStep> steps, boolean complete, int evolveCandy, PowerUpCost.Cost secondMove) {}

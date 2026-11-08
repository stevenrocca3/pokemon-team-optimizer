package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Combatant;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Moveset;

/** One Pokémon of a League's meta group, built the way PvPoke battles it. {@code id} is PvPoke's ("annihilape_shadow"). */
public record MetaPokemon(String id, Combatant combatant, Moveset moveset) {}

package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GamemasterMove(String moveId, String name, String type, int power, int energy, int energyGain, int turns,
    List<Integer> buffs, String buffTarget, String buffApplyChance, List<String> tags, String damageMethod)
{
    public GamemasterMove
    {
        if (tags == null) tags = List.of();
    }
}

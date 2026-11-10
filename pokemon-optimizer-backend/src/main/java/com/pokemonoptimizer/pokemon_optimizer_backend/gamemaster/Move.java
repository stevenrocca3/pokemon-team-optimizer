package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;

import java.util.List;

/**
 * tags: PvPoke's move tags, e.g. "instant" (no minigame, can't be shielded) and "ignoresFaint" (fires even if its user
 * was just knocked out), both on Cramorant's Gulp Missile. damageMethod: null for the usual formula, or "percentMaxHP"
 * (power is a percentage of the target's max HP).
 */
public record Move(String moveId, String name, String type, int power, int energy, int energyGain, int turns,
    List<Integer> buffs, String buffTarget, double buffApplyChance, List<String> tags, String damageMethod)
{
    public boolean hasTag(String tag)
    {
        return tags.contains(tag);
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.OptionalDouble;

import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.CpCalculator;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

/** The open GO Battle League formats the Team Builder supports. */
public enum League
{
    GREAT("great", 1500),
    ULTRA("ultra", 2500),
    MASTER("master", 0);   // no CP Cap

    /** Highest level reachable without Best Buddy (XL Candy included). */
    static final double MAX_LEVEL = 50;

    /** PvPoke's name for the League's meta group and defaultIVs key. */
    final String key;
    /** 0 for no Cap. */
    final int cpCap;

    League(String key, int cpCap)
    {
        this.key = key;
        this.cpCap = cpCap;
    }

    /** The League with PvPoke's CP cap (1500, 2500, or 10000 for no cap). */
    public static League ofPvpokeCap(int cp)
    {
        return switch (cp)
        {
            case 1500 -> GREAT;
            case 2500 -> ULTRA;
            case 10000 -> MASTER;
            default -> throw new IllegalArgumentException("No League has a " + cp + " CP cap");
        };
    }

    /**
     * The level this Pokémon battles at once built for the League: the highest level whose CP fits the Cap
     * (at most 50), but never below its current level. Empty if it is already over the Cap.
     */
    OptionalDouble buildLevel(SpeciesType species, int atkIv, int defIv, int staIv, double currentLevel)
    {
        if (cpCap != 0 && cp(species, atkIv, defIv, staIv, currentLevel) > cpCap)
        {
            return OptionalDouble.empty();
        }
        double highest = CpCalculator.findMaxLevel(species.baseAtk(), species.baseDef(), species.baseSta(),
            atkIv, defIv, staIv, cpCap != 0, cpCap, false);
        return OptionalDouble.of(Math.max(currentLevel, Math.min(highest, MAX_LEVEL)));
    }

    static int cp(SpeciesType species, int atkIv, int defIv, int staIv, double level)
    {
        return CpCalculator.calculateCP(species.baseAtk(), species.baseDef(), species.baseSta(), atkIv, defIv, staIv, level);
    }
}

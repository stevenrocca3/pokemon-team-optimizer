package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.CpCalculator;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

public record Combatant(SpeciesType species, int atkIv, int defIv, int staIv, double level, boolean shadow)
{
    private static final double SHADOW_ATK = 1.2;
    private static final double SHADOW_DEF = 0.83333331;   
    public double attack() 
    { 
        double shadowAtk = shadow ? SHADOW_ATK : 1.0;
        return (species.baseAtk() + atkIv) * cpm() * shadowAtk;
    }
    public double defense() 
    { 
        double shadowDef = shadow ? SHADOW_DEF : 1.0;
        return (species.baseDef() + defIv) * cpm() * shadowDef;
    }
    public int maxHp() { return Math.max(10, (int) Math.floor((species.baseSta() + staIv) * cpm()));}
    private double cpm() { return CpCalculator.CPM_TABLE[(int)(level * 2.0)];} 
}

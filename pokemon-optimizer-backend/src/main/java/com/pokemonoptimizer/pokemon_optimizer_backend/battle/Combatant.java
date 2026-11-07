package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.CpCalculator;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

public record Combatant(SpeciesType species, int atkIv, int defIv, int staIv, double level, boolean shadow)
{
    private static final double SHADOW_ATK = 1.2;
    private static final double SHADOW_DEF = 0.83333331;   
    public double attack() 
    { 
        return attack(0);
    }

    public double attack(int stage)
    {
        double shadowAtk = shadow ? SHADOW_ATK : 1.0;
        return StatStages.multiplier(stage) * shadowAtk * ((species.baseAtk() + atkIv) * cpm());
    }
    public double defense() 
    { 
        return defense(0);
    }

    public double defense(int stage)
    {
        double shadowDef = shadow ? SHADOW_DEF : 1.0;
        return StatStages.multiplier(stage) * shadowDef * ((species.baseDef() + defIv) * cpm());
    }
    public int maxHp() { return Math.max(10, (int) Math.floor((species.baseSta() + staIv) * cpm()));}
    private double cpm() { return CpCalculator.CPM_TABLE[(int)(level * 2.0)];} 
}

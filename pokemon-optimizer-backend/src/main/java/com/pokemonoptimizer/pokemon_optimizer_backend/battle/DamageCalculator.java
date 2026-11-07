package com.pokemonoptimizer.pokemon_optimizer_backend.battle;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;


public final class DamageCalculator {
    
    private DamageCalculator() {}
    private static final double BONUS = 1.2999999523162841796875;
    private static final double STAB = 1.2000000476837158203125;

    public static int damage(int power, double attack, double defense, boolean stab, double effectiveness)
    {
        double stabMult = stab ? STAB : 1.0;
        return (int) Math.floor(power * stabMult * (attack / defense) * effectiveness * 0.5 * BONUS) + 1;
        
    }
    public static int damage(Combatant attacker, Combatant defender, Move move)
    {
        return damage(attacker, 0, defender, 0, move);

    }
    public static int damage(Combatant attacker, int attackStage, Combatant defender, int defenseStage, Move move)
    {
        boolean stab = attacker.species().types().contains(move.type());
        double effectiveness = TypeChart.effectiveness(move.type(), defender.species().types());
        return damage(move.power(), attacker.attack(attackStage), defender.defense(defenseStage), stab, effectiveness);
    }

}

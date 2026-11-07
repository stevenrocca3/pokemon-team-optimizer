package com.pokemonoptimizer.pokemon_optimizer_backend.battle;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TypeChart
{
    private static final double SE = 1.60000002384185791015625;
    private static final double RESISTED = 0.625;
    private static final double IMM = 0.390625;

     private static final Map<String, Set<String>> SUPER_EFFECTIVE = Map.ofEntries(
        Map.entry("normal",   Set.of()),
        Map.entry("fire",     Set.of("grass", "ice", "bug", "steel")),
        Map.entry("water",    Set.of("fire", "ground", "rock")),
        Map.entry("electric", Set.of("water", "flying")),
        Map.entry("grass",    Set.of("water", "ground", "rock")),
        Map.entry("ice",      Set.of("grass", "ground", "flying", "dragon")),
        Map.entry("fighting", Set.of("normal", "ice", "rock", "dark", "steel")),
        Map.entry("poison",   Set.of("grass", "fairy")),
        Map.entry("ground",   Set.of("fire", "electric", "poison", "rock", "steel")),
        Map.entry("flying",   Set.of("grass", "fighting", "bug")),
        Map.entry("psychic",  Set.of("fighting", "poison")),
        Map.entry("bug",      Set.of("grass", "psychic", "dark")),
        Map.entry("rock",     Set.of("fire", "ice", "flying", "bug")),
        Map.entry("ghost",    Set.of("psychic", "ghost")),
        Map.entry("dragon",   Set.of("dragon")),
        Map.entry("dark",     Set.of("psychic", "ghost")),
        Map.entry("steel",    Set.of("ice", "rock", "fairy")),
        Map.entry("fairy",    Set.of("fighting", "dragon", "dark")));

    private static final Map<String, Set<String>> RESISTED_BY = Map.ofEntries(
        Map.entry("normal",   Set.of("rock", "steel")),
        Map.entry("fire",     Set.of("fire", "water", "rock", "dragon")),
        Map.entry("water",    Set.of("water", "grass", "dragon")),
        Map.entry("electric", Set.of("electric", "grass", "dragon")),
        Map.entry("grass",    Set.of("fire", "grass", "poison", "flying", "bug", "dragon", "steel")),
        Map.entry("ice",      Set.of("fire", "water", "ice", "steel")),
        Map.entry("fighting", Set.of("poison", "flying", "psychic", "bug", "fairy")),
        Map.entry("poison",   Set.of("poison", "ground", "rock", "ghost")),
        Map.entry("ground",   Set.of("grass", "bug")),
        Map.entry("flying",   Set.of("electric", "rock", "steel")),
        Map.entry("psychic",  Set.of("psychic", "steel")),
        Map.entry("bug",      Set.of("fire", "fighting", "poison", "flying", "ghost", "steel", "fairy")),
        Map.entry("rock",     Set.of("fighting", "ground", "steel")),
        Map.entry("ghost",    Set.of("dark")),
        Map.entry("dragon",   Set.of("steel")),
        Map.entry("dark",     Set.of("fighting", "dark", "fairy")),
        Map.entry("steel",    Set.of("fire", "water", "electric", "steel")),
        Map.entry("fairy",    Set.of("fire", "poison", "steel")));

    private static final Map<String, Set<String>> IMMUNE = Map.ofEntries(
        Map.entry("normal",   Set.of("ghost")),
        Map.entry("electric", Set.of("ground")),
        Map.entry("fighting", Set.of("ghost")),
        Map.entry("poison",   Set.of("steel")),
        Map.entry("ground",   Set.of("flying")),
        Map.entry("psychic",  Set.of("dark")),
        Map.entry("ghost",    Set.of("normal")),
        Map.entry("dragon",   Set.of("fairy")));
    
    private TypeChart() {}

    

    public static double effectiveness(String moveType, List<String> defenderTypes)
    {
        double result = 1.0;
        for (String defenderType : defenderTypes)
        {
            if (IMMUNE.getOrDefault(moveType, Set.of()).contains(defenderType))
            {
                result *= IMM;
            }
            else if (RESISTED_BY.getOrDefault(moveType, Set.of()).contains(defenderType))
            {
                result *= RESISTED;
            }
            else if (SUPER_EFFECTIVE.getOrDefault(moveType, Set.of()).contains(defenderType))
            {
                result *= SE;
            }
            
        }
        return result;
    }

}

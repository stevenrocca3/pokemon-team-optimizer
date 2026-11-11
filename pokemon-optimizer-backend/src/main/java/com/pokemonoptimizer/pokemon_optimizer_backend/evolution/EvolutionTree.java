package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Family;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

/**
 * Pokémon evolution lines as a graph, from the gamemaster's family data: each form points to what it evolves into.
 * Branches (Eevee, Tyrogue, Wurmple) and regional lines (Galarian Slowpoke) are just more edges. The walk goes through
 * forms PvPoke marks unreleased (Spewpa) so the released ones after them still show, and skips IDs that don't exist.
 */
@Component
public class EvolutionTree
{
    private final SpeciesLookup species;

    public EvolutionTree(SpeciesLookup species)
    {
        this.species = species;
    }

    /** Every released form this species can evolve into, nearest stage first, each with the line that leads to it. */
    public List<EvolvedForm> evolutionsOf(String speciesId)
    {
        List<EvolvedForm> found = new ArrayList<>();
        // breadth first, so stage 1 forms come before stage 2
        Deque<List<String>> lines = new ArrayDeque<>();
        lines.add(List.of(speciesId));
        while (!lines.isEmpty())
        {
            List<String> line = lines.poll();
            String last = line.get(line.size() - 1);
            List<String> next = species.family(last).map(Family::evolutions).orElse(null);
            if (next == null)
            {
                continue;
            }
            for (String child : next)
            {
                if (species.findForm(child).isEmpty() || line.contains(child))
                {
                    continue;   // a typo'd ID in PvPoke's data, or a loop
                }
                List<String> longer = new ArrayList<>(line);
                longer.add(child);
                if (species.findById(child).isPresent())
                {
                    found.add(new EvolvedForm(child, longer.size() - 1, List.copyOf(longer)));
                }
                lines.add(longer);
            }
        }
        return List.copyOf(found);
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesTraits;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

/**
 * A species as PvPoke's Cup filters see it. A Shadow is its own entrant, the way PvPoke clones it: id + "_shadow",
 * tagged "shadow" and no longer "shadoweligible" or "wildlegendary".
 */
public record CupEntrant(String id, int dex, List<String> types, List<String> tags, int evolutionStage, Set<String> moves)
{
    public static CupEntrant of(SpeciesType species, SpeciesTraits traits, boolean shadow)
    {
        Set<String> moves = new HashSet<>(species.learnset().fastMoves());
        moves.addAll(species.learnset().chargedMoves());
        moves.addAll(species.learnset().eliteMoves());
        List<String> tags = traits.tags();
        if (shadow)
        {
            tags = new java.util.ArrayList<>(tags.stream().filter(t -> !t.equals("shadoweligible") && !t.equals("wildlegendary")).toList());
            tags.add("shadow");
        }
        return new CupEntrant(shadow ? species.speciesId() + "_shadow" : species.speciesId(), species.dex(), species.types(),
            List.copyOf(tags), traits.evolutionStage(), Set.copyOf(moves));
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.Evolution;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.EvolvedPokemon;

/**
 * The Team Builder's "Include Evolvable Pokémon" (#138): each of the Trainer's Pokémon as every released form it can
 * evolve into. Evolving keeps IVs, level and Shadow status but rerolls the moves, so each form battles with its best
 * moveset, and carries a warning when that needs an exclusive move. A form with no known best moveset is left out.
 */
@Component
public class EvolvableCandidates
{
    private final EvolutionTree tree;
    private final EvolutionMoves moves;

    public EvolvableCandidates(EvolutionTree tree, EvolutionMoves moves)
    {
        this.tree = tree;
        this.moves = moves;
    }

    public List<EvolvedPokemon> expand(List<CaughtPokemonRequest> pokemon)
    {
        List<EvolvedPokemon> evolved = new ArrayList<>();
        for (int i = 0; i < pokemon.size(); i++)
        {
            CaughtPokemonRequest original = pokemon.get(i);
            for (EvolvedForm form : tree.evolutionsOf(original.speciesId()))
            {
                Optional<FormMoves> best = moves.forForm(form.speciesId()).filter(m -> m.optimalFastMove() != null);
                if (best.isEmpty())
                {
                    continue;
                }
                CaughtPokemonRequest asForm = new CaughtPokemonRequest(form.speciesId(), original.atkIv(), original.defIv(),
                    original.staIv(), original.level(), best.get().optimalFastMove(), best.get().optimalChargedMoves(), original.shadow());
                List<String> warnings = best.get().warnings().stream().map(MoveWarning::message).toList();
                evolved.add(new EvolvedPokemon(i, asForm, new Evolution(original.speciesId(), warnings)));
            }
        }
        return List.copyOf(evolved);
    }
}

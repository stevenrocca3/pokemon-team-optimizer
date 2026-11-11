package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.PowerUpCost;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Family;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.BuildCost;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.EvolutionStep;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.Evolution;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.EvolvedPokemon;

/**
 * The Team Builder's "Include Evolvable Pokémon" (#138): each of the Trainer's Pokémon as every released form it can
 * evolve into. Evolving keeps IVs, level and Shadow status but rerolls the moves, so each form battles with its best
 * moveset, and carries a warning when that needs an exclusive move, and what evolving it costs. A form with no known
 * best moveset is left out.
 */
@Component
public class EvolvableCandidates
{
    private final EvolutionTree tree;
    private final EvolutionMoves moves;
    private final EvolutionCosts costs;
    private final SpeciesLookup species;

    public EvolvableCandidates(EvolutionTree tree, EvolutionMoves moves, EvolutionCosts costs, SpeciesLookup species)
    {
        this.tree = tree;
        this.moves = moves;
        this.costs = costs;
        this.species = species;
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
                evolved.add(new EvolvedPokemon(i, asForm, new Evolution(original.speciesId(), warnings, cost(original, form, asForm.chargedMoves().size()))));
            }
        }
        return List.copyOf(evolved);
    }

    /** GO's shadowThirdMoveUnlock multipliers, Stardust and Candy alike */
    private static final double SHADOW_SECOND_MOVE = 1.2;

    /** The Candy, items and second move evolving {@code original} into {@code form} takes (#139). */
    private BuildCost cost(CaughtPokemonRequest original, EvolvedForm form, int chargedMoves)
    {
        List<EvolutionStep> steps = new ArrayList<>();
        boolean complete = true;
        for (int s = 1; s < form.path().size(); s++)
        {
            Optional<EvolutionStep> step = costs.step(form.path().get(s - 1), form.path().get(s));
            step.ifPresent(steps::add);
            complete &= step.isPresent();
        }
        int candy = steps.stream().mapToInt(EvolutionStep::candy).sum();
        PowerUpCost.Cost secondMove = chargedMoves < 2 ? null : costs.secondMove(form.speciesId())
            .map(c -> original.shadow() ? new PowerUpCost.Cost(shadowed(c.stardust()), shadowed(c.candy()), 0) : c)
            .orElse(null);
        String family = species.family(original.speciesId()).map(Family::id).orElse(null);
        return new BuildCost(family, List.copyOf(steps), complete, candy, secondMove);
    }

    private static int shadowed(int cost)
    {
        return (int) Math.ceil(cost * SHADOW_SECOND_MOVE - 1e-9);
    }
}

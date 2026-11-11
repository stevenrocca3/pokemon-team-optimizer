package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMoves;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMovesets;

/**
 * What an evolved form can learn, and whether its best moveset needs an exclusive move. Exclusives are the gamemaster's
 * elite and legacy moves: Community Day and event moves, and moves removed from the regular pool. Evolving gives a
 * random regular move, so a best set that needs one takes an Elite TM, or evolving during the event that has it.
 */
@Component
public class EvolutionMoves
{
    private final SpeciesLookup species;
    private final MoveLookup moves;
    private final SuggestedMovesets suggested;

    public EvolutionMoves(SpeciesLookup species, MoveLookup moves, SuggestedMovesets suggested)
    {
        this.species = species;
        this.moves = moves;
        this.suggested = suggested;
    }

    /** Empty for an unknown species. With no PvPoke recommendation, the best moveset is null and there are no warnings. */
    public Optional<FormMoves> forForm(String speciesId)
    {
        return species.findById(speciesId).map(form -> {
            Set<String> exclusive = form.learnset().eliteMoves();
            Set<String> legacy = species.legacyMoves(speciesId);
            Movepool pool = new Movepool(options(form.learnset().fastMoves(), exclusive, legacy),
                options(form.learnset().chargedMoves(), exclusive, legacy));
            Optional<SuggestedMoves> best = suggested.forSpecies(speciesId);
            if (best.isEmpty())
            {
                return new FormMoves(pool, null, List.of(), List.of());
            }
            List<MoveWarning> warnings = new ArrayList<>();
            if (exclusive.contains(best.get().fastMove()))
            {
                warnings.add(warning(form, best.get().fastMove(), true));
            }
            for (String charged : best.get().chargedMoves())
            {
                if (exclusive.contains(charged))
                {
                    warnings.add(warning(form, charged, false));
                }
            }
            return new FormMoves(pool, best.get().fastMove(), best.get().chargedMoves(), List.copyOf(warnings));
        });
    }

    private List<MoveOption> options(List<String> ids, Set<String> exclusive, Set<String> legacy)
    {
        return ids.stream().map(id -> new MoveOption(id, nameOf(id), exclusive.contains(id), legacy.contains(id))).toList();
    }

    private MoveWarning warning(SpeciesType form, String moveId, boolean fast)
    {
        String name = nameOf(moveId);
        String tm = fast ? "an Elite Fast TM" : "an Elite Charged TM";
        return new MoveWarning(moveId, name, fast,
            form.speciesName() + " needs " + name + ": " + tm + ", or evolving it during an event that has the move, like its Community Day.");
    }

    private String nameOf(String moveId)
    {
        return moves.findById(moveId).map(Move::name).orElse(moveId);
    }
}

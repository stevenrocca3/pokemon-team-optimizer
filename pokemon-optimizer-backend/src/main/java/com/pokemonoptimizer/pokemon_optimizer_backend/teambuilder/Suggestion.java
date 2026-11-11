package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;

/**
 * A meta Pokémon to swap into a Team winning under half its battles (#112), built the way PvPoke battles it (its
 * default IVs and level for the League, its meta moveset). {@code replaces} is the request index of the member it
 * takes the place of. {@code coverageGain} is how much the Team's meta coverage (its best answer's average battle
 * rating against each meta Pokémon) rises with the swap. {@code covers} are the meta Pokémon (PvPoke IDs) it helps
 * against most.
 */
public record Suggestion(String speciesId, boolean shadow, Role role, int replaces, String replacesSpeciesId,
                         double level, int cp, int atkIv, int defIv, int staIv, String fastMove, List<String> chargedMoves,
                         double coverageGain, List<String> covers)
{
    /**
     * Where it plays best, from its ratings against the meta at each shield count: with shields up it starts battles
     * (Lead), with one each it takes the hits a switch brings (Safe Swap), with none left it finishes them (Switcher/Closer).
     */
    public enum Role
    {
        LEAD("Lead"), SAFE_SWAP("Safe Swap"), CLOSER("Switcher/Closer");

        public final String label;

        Role(String label)
        {
            this.label = label;
        }
    }
}

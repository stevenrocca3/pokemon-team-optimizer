package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Combatant;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.Cup;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.CupCatalog;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;

/**
 * A meta Pokémon as PvPoke builds it (#114): its default IVs and level for the League, and its meta moveset. What a
 * suggested swap is added to a Collection as, so the Team Builder battles it exactly as it was suggested.
 */
@Component
public class MetaPresets
{
    private final MetaLookup metaLookup;
    private final CupCatalog cups;

    public MetaPresets(MetaLookup metaLookup, CupCatalog cups)
    {
        this.metaLookup = metaLookup;
        this.cups = cups;
    }

    /** Empty when it isn't in that meta (the League's, or the Cup's when {@code cupKey} names a running one). */
    public Optional<CaughtPokemonRequest> preset(League league, String cupKey, String speciesId, boolean shadow)
    {
        List<MetaPokemon> meta = cupKey == null ? metaLookup.meta(league) : cups.find(cupKey).map(Cup::meta).orElse(List.of());
        return meta.stream()
            .filter(p -> p.combatant().species().speciesId().equals(speciesId) && p.combatant().shadow() == shadow)
            .findFirst()
            .map(p -> {
                Combatant c = p.combatant();
                return new CaughtPokemonRequest(speciesId, c.atkIv(), c.defIv(), c.staIv(), c.level(), p.moveset().fastMove().moveId(),
                    p.moveset().chargedMoves().stream().map(Move::moveId).toList(), shadow);
            });
    }
}

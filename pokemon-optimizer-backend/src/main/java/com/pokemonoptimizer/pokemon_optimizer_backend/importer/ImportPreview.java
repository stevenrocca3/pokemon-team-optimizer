package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

import java.util.List;
import java.util.Map;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Learnset;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMoves;

/**
 * What an import would add, before anything is saved. {@code learnsets} covers every species that
 * needs moves, so the page can offer move pickers without a request per species; {@code suggestedMoves} is the
 * moveset each picker starts on, for those of them PvPoke ranks.
 */
public record ImportPreview(List<ImportedPokemon> pokemon, List<RejectedRow> rejected, Map<String, Learnset> learnsets,
                            Map<String, SuggestedMoves> suggestedMoves) {}

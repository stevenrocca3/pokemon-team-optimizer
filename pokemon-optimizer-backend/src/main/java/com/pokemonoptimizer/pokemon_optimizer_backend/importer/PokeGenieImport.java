package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

import java.util.List;

/** The result of reading a Poké Genie export: the rows that parsed, and the ones that didn't with why. */
public record PokeGenieImport(List<PokeGenieRow> rows, List<RejectedRow> rejected) {}

package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.util.List;

/** One sync's result: the usable formats and a note for everything left out or dropped, with why. */
public record ParsedCups(String sourceTimestamp, List<CupFormat> formats, List<String> notes) {}

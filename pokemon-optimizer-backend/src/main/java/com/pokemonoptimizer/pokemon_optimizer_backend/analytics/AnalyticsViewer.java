package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;

/**
 * Who is asking an analytics endpoint (#121): a logged-in Player, or a Guest. Handlers take it as a parameter
 * (AnalyticsViewerResolver fills it in from the session), so none of them reads the session itself.
 */
public sealed interface AnalyticsViewer
{
    record Guest() implements AnalyticsViewer {}

    record Player(Trainer trainer) implements AnalyticsViewer {}
}

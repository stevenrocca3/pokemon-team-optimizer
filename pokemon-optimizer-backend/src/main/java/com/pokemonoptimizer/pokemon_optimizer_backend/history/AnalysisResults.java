package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import tools.jackson.databind.JsonNode;

/** A finished analysis's result ({turn_log, report}), from the battle analysis service. */
public interface AnalysisResults
{
    /**
     * @throws AnalysisNotFoundException the service doesn't know the job (never existed, or expired after a day)
     * @throws AnalysisNotFinishedException the job is still running, or failed
     */
    JsonNode result(String jobId);
}

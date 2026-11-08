package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What a player typed in the feedback box, and the page they were on. No name or contact: nothing personal is kept. */
public record FeedbackRequest(@NotBlank @Size(max = 2000) String message, @Size(max = 200) String page) {}

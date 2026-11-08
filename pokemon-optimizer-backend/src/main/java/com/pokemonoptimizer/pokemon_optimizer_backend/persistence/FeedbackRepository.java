package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {}

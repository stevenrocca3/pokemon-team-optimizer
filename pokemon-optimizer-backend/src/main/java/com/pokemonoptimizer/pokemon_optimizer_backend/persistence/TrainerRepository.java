package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainerRepository extends JpaRepository<Trainer, Long>
{
    Optional<Trainer> findByGoogleId(String googleId);
}

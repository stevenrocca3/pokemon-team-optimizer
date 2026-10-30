package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;   

public interface CaughtPokemonRepository extends JpaRepository<CaughtPokemon, Long>
{
    List<CaughtPokemon> findByTrainer(Trainer trainer);
    Optional<CaughtPokemon> findByIdAndTrainer(Long id, Trainer trainer);
}

package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CaughtPokemonRepository extends JpaRepository<CaughtPokemon, Long>
{
    List<CaughtPokemon> findByTrainer(Trainer trainer);
    Optional<CaughtPokemon> findByIdAndTrainer(Long id, Trainer trainer);
    long countByTrainer(Trainer trainer);

    /** One DELETE statement, not one per Pokémon: a Collection can hold thousands. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)   // it skips the session, so sync before and after
    @Query("DELETE FROM CaughtPokemon c WHERE c.trainer = :trainer")
    void deleteByTrainer(Trainer trainer);
}

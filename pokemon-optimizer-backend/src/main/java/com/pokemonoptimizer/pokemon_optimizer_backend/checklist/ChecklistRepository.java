package com.pokemonoptimizer.pokemon_optimizer_backend.checklist;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;

public interface ChecklistRepository extends JpaRepository<ChecklistEntry, Long>
{
    List<ChecklistEntry> findByTrainer(Trainer trainer);

    /** One statement, run right away: Hibernate would otherwise flush new rows before the deletes and trip the unique key. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ChecklistEntry e WHERE e.trainer = :trainer")
    void deleteByTrainer(Trainer trainer);
}

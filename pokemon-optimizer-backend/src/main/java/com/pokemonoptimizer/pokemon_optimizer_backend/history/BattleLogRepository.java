package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;

public interface BattleLogRepository extends JpaRepository<BattleLog, UUID>
{
    /** A Trainer's whole battle history; their user_match_history rows go with it (ON DELETE CASCADE). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM battle_logs WHERE match_id IN (SELECT match_id FROM user_match_history WHERE trainer_id = :#{#trainer.id})",
           nativeQuery = true)
    int deleteOwnedBy(Trainer trainer);
}

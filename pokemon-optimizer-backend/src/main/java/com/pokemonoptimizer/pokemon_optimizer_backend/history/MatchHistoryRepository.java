package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;

public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long>
{
    Optional<MatchHistory> findByBattleMatchId(UUID matchId);

    List<MatchHistory> findByGuestToken(UUID guestToken);

    List<MatchHistory> findByTrainerOrderByRecordedAtAsc(Trainer trainer);

    /** The Trainer's battles, newest first, without each one's full result. */
    @Query("""
        SELECT new com.pokemonoptimizer.pokemon_optimizer_backend.history.BattleRow(b.matchId, b.createdAt,
            b.detectedUserTeam, b.detectedOppTeam, b.overallExecutionScore, b.tier, b.outcome, b.turnsLost,
            b.shieldEfficiencyPct, b.timingAccuracyPct, b.timingErrors, b.mismanagedShields, b.suboptimalSwitches,
            b.suboptimalCharges)
        FROM MatchHistory h JOIN h.battle b WHERE h.trainer = :trainer ORDER BY h.recordedAt DESC""")
    List<BattleRow> rowsNewestFirst(Trainer trainer);

    /** A battle's full result, if it's the Trainer's. */
    @Query("SELECT b.result FROM MatchHistory h JOIN h.battle b WHERE h.trainer = :trainer AND b.matchId = :matchId")
    Optional<String> resultFor(Trainer trainer, UUID matchId);
}

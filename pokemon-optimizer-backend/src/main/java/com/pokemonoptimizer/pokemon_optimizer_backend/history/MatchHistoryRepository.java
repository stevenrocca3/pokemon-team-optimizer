package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;

public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long>
{
    Optional<MatchHistory> findByBattleMatchId(UUID matchId);

    List<MatchHistory> findByGuestToken(UUID guestToken);

    List<MatchHistory> findByTrainerOrderByRecordedAtAsc(Trainer trainer);
}

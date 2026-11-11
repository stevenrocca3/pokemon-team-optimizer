package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pokemonoptimizer.pokemon_optimizer_backend.history.BattleRow;
import com.pokemonoptimizer.pokemon_optimizer_backend.history.MatchHistoryRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;

/** A logged-in player's battle analytics, computed from their history when asked (it's small, and never stale). */
@Service
public class AnalyticsService
{
    /** Battles before the rank score shows (spec §3.2). */
    static final int RANK_UNLOCKS_AT = 10;
    /** The rank is how the player plays now: their last 25 battles (the longest rolling window, spec §2.1). */
    static final int RANK_WINDOW = 25;
    static final int HISTORY_LIMIT = 50;

    /** The match tiers (battle-analysis scoring.TIERS), applied to the rank score. */
    private static final List<Floor> TIERS = List.of(
        new Floor(90, new AnalyticsSummary.Tier("legend", "Legend Tier Execution")),
        new Floor(75, new AnalyticsSummary.Tier("veteran", "Veteran / Expert Tier Execution")),
        new Floor(50, new AnalyticsSummary.Tier("ace", "Ace / Mid-Ladder Execution")),
        new Floor(0, new AnalyticsSummary.Tier("critical", "Critical Execution Flaws (Needs Major Rebuilding)")));

    private record Floor(int min, AnalyticsSummary.Tier tier) {}

    private final MatchHistoryRepository history;

    public AnalyticsService(MatchHistoryRepository history)
    {
        this.history = history;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummary summary(Trainer trainer)
    {
        List<BattleRow> newestFirst = history.rowsNewestFirst(trainer);
        return new AnalyticsSummary("full", newestFirst.size(),
            newestFirst.subList(0, Math.min(HISTORY_LIMIT, newestFirst.size())), rank(newestFirst));
    }

    @Transactional(readOnly = true)
    public Optional<String> battleResult(Trainer trainer, UUID matchId)
    {
        return history.resultFor(trainer, matchId);
    }

    /** Locked under 10 battles, with nothing about the score in it; then the last 25 battles' average and its tier. */
    static AnalyticsSummary.Rank rank(List<BattleRow> newestFirst)
    {
        int battles = newestFirst.size();
        if (battles < RANK_UNLOCKS_AT)
        {
            return new AnalyticsSummary.Rank(true, battles, RANK_UNLOCKS_AT, RANK_UNLOCKS_AT - battles, null, null);
        }
        List<BattleRow> recent = newestFirst.subList(0, Math.min(RANK_WINDOW, battles));
        BigDecimal total = recent.stream().map(BattleRow::score).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal score = total.divide(BigDecimal.valueOf(recent.size()), 1, RoundingMode.HALF_UP);
        AnalyticsSummary.Tier tier = TIERS.stream()
            .filter(f -> score.compareTo(BigDecimal.valueOf(f.min())) >= 0)
            .findFirst().orElseThrow().tier();
        return new AnalyticsSummary.Rank(false, battles, RANK_UNLOCKS_AT, null, score, tier);
    }
}

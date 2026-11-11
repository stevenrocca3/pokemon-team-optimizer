package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.pokemonoptimizer.pokemon_optimizer_backend.history.BattleRow;

/**
 * A player's top 3 teams (#123, spec §2.2). A team is the set of three species, so the same three with a different
 * lead are one team. Ranked by average execution score × 0.6 + win rate % × 0.4, among teams with at least 3 battles.
 * <p>
 * Win rate is estimated: a recording that stops early has no result, so it's (wins + 1) / (known results + 2). That
 * is 50% with no results known, and closes in on the real rate as results come in.
 */
public final class TeamRanker
{
    static final int MIN_BATTLES = 3;
    static final int TOP = 3;
    private static final BigDecimal EXECUTION_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal WIN_RATE_WEIGHT = new BigDecimal("0.4");

    private TeamRanker() {}

    public static List<TeamRanking> topTeams(List<BattleRow> newestFirst)
    {
        // newest first, so each team's first battle seen is its latest: its lead order is the one shown
        Map<List<String>, List<BattleRow>> byTeam = new LinkedHashMap<>();
        Map<List<String>, List<String>> shownAs = new LinkedHashMap<>();
        for (BattleRow row : newestFirst)
        {
            if (row.userTeam() == null || row.userTeam().size() != 3)
            {
                continue;
            }
            List<String> key = row.userTeam().stream().sorted().toList();
            byTeam.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            shownAs.putIfAbsent(key, row.userTeam());
        }
        return byTeam.entrySet().stream()
            .filter(e -> e.getValue().size() >= MIN_BATTLES)
            .map(e -> rank(shownAs.get(e.getKey()), e.getValue()))
            .sorted(Comparator.comparing(TeamRanking::rankScore).reversed()
                .thenComparing(Comparator.comparingInt(TeamRanking::battles).reversed())
                .thenComparing(t -> String.join(",", t.team())))
            .limit(TOP)
            .toList();
    }

    private static TeamRanking rank(List<String> team, List<BattleRow> battles)
    {
        int wins = (int) battles.stream().filter(b -> "WIN".equals(b.outcome())).count();
        int losses = (int) battles.stream().filter(b -> "LOSS".equals(b.outcome())).count();
        BigDecimal average = battles.stream().map(BattleRow::score).reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(battles.size()), 4, RoundingMode.HALF_UP);
        BigDecimal winRate = BigDecimal.valueOf(100L * (wins + 1))
            .divide(BigDecimal.valueOf(wins + losses + 2), 4, RoundingMode.HALF_UP);
        BigDecimal rankScore = average.multiply(EXECUTION_WEIGHT).add(winRate.multiply(WIN_RATE_WEIGHT));
        return new TeamRanking(team, battles.size(), wins, losses, one(average), one(winRate), one(rankScore));
    }

    private static BigDecimal one(BigDecimal value)
    {
        return value.setScale(1, RoundingMode.HALF_UP);
    }
}

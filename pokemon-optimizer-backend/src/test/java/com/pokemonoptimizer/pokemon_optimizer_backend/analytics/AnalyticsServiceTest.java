package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.pokemonoptimizer.pokemon_optimizer_backend.history.BattleLog;
import com.pokemonoptimizer.pokemon_optimizer_backend.history.BattleRow;
import com.pokemonoptimizer.pokemon_optimizer_backend.history.MatchHistory;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;

/** Issue #121: what a logged-in player's analytics hold: their history, and a rank that stays locked under 10 battles. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AnalyticsService.class)
public class AnalyticsServiceTest
{
    private static final Instant START = Instant.parse("2026-11-01T12:00:00Z");

    @Autowired private AnalyticsService analytics;
    @Autowired private TestEntityManager entityManager;

    private Trainer ash;
    private Trainer misty;
    private int played;

    @BeforeEach
    void setUp()
    {
        ash = entityManager.persist(new Trainer("google-ash"));
        misty = entityManager.persist(new Trainer("google-misty"));
    }

    /** One more battle in `owner`'s history (a minute after the last), scoring `score`; returns its match id. */
    private UUID battle(Trainer owner, double score, String outcome)
    {
        Instant at = START.plus(played++, ChronoUnit.MINUTES);
        UUID id = UUID.randomUUID();
        BattleLog log = entityManager.persist(new BattleLog(id, at, List.of("Swampert", "Cradily", "Clodsire"),
            List.of("Morpeko", "Gastrodon", "Doublade"), BigDecimal.valueOf(score), "ace", outcome, 4, null,
            new BigDecimal("66.7"), 2, 0, 1, 0, "{\"report\": {\"score\": " + score + "}}"));
        entityManager.persist(new MatchHistory(log, owner, null, at));
        return id;
    }

    private void battles(Trainer owner, int count, double score)
    {
        for (int i = 0; i < count; i++)
        {
            battle(owner, score, "WIN");
        }
    }

    private AnalyticsSummary summaryOf(Trainer trainer)
    {
        entityManager.flush();
        entityManager.clear();
        return analytics.summary(trainer);
    }

    // The history lists the player's own battles, newest first, with what the dashboard shows of each; another
    // player's battles never appear.
    @Test
    public void historyIsThePlayersOwnBattlesNewestFirst()
    {
        UUID first = battle(ash, 61, "WIN");
        battle(misty, 90, "LOSS");
        UUID second = battle(ash, 88, null);

        AnalyticsSummary summary = summaryOf(ash);

        assertThat(summary.access()).isEqualTo("full");
        assertThat(summary.battles()).isEqualTo(2);
        assertThat(summary.history()).extracting(BattleRow::matchId).containsExactly(second, first);
        BattleRow older = summary.history().get(1);
        assertThat(older.userTeam()).containsExactly("Swampert", "Cradily", "Clodsire");
        assertThat(older.oppTeam()).containsExactly("Morpeko", "Gastrodon", "Doublade");
        assertThat(older.score()).isEqualByComparingTo("61");
        assertThat(older.outcome()).isEqualTo("WIN");
        assertThat(older.timingAccuracyPct()).isEqualByComparingTo("66.7");
        assertThat(summary.history().get(0).outcome()).isNull();
    }

    // Under 10 battles the rank is locked: how many there are and how many more are needed, but no score and no tier
    // (so the hidden rank isn't in the response at all).
    @Test
    public void rankIsLockedUnderTenBattles()
    {
        battles(ash, 6, 95);

        AnalyticsSummary.Rank rank = summaryOf(ash).rank();

        assertThat(rank.locked()).isTrue();
        assertThat(rank.battles()).isEqualTo(6);
        assertThat(rank.required()).isEqualTo(10);
        assertThat(rank.remaining()).isEqualTo(4);
        assertThat(rank.score()).isNull();
        assertThat(rank.tier()).isNull();
    }

    // The 10th battle unlocks it: the rank score is the average execution score (here 70 and 80, five each: 75.0),
    // and its tier is the match tiers' (75 and up is Veteran).
    @Test
    public void theTenthBattleUnlocksTheRank()
    {
        battles(ash, 5, 70);
        battles(ash, 5, 80);

        AnalyticsSummary.Rank rank = summaryOf(ash).rank();

        assertThat(rank.locked()).isFalse();
        assertThat(rank.battles()).isEqualTo(10);
        assertThat(rank.remaining()).isNull();
        assertThat(rank.score()).isEqualByComparingTo("75.0");
        assertThat(rank.tier()).isEqualTo(new AnalyticsSummary.Tier("veteran", "Veteran / Expert Tier Execution"));
    }

    // The rank reflects how the player plays now: the last 25 battles. Five early 0s don't drag down 25 battles at 80
    // (all 30 would average 66.7, Ace).
    @Test
    public void rankAveragesTheLast25Battles()
    {
        battles(ash, 5, 0);
        battles(ash, 25, 80);

        AnalyticsSummary.Rank rank = summaryOf(ash).rank();

        assertThat(rank.score()).isEqualByComparingTo("80.0");
        assertThat(rank.tier().key()).isEqualTo("veteran");
    }

    // A player with no battles yet: zero, an empty history, and a locked rank needing all 10.
    @Test
    public void aPlayerWithNoBattles()
    {
        AnalyticsSummary summary = summaryOf(ash);

        assertThat(summary.battles()).isZero();
        assertThat(summary.history()).isEmpty();
        assertThat(summary.rank().locked()).isTrue();
        assertThat(summary.rank().remaining()).isEqualTo(10);
    }

    // The history in the payload is the newest 50; the count is all of them.
    @Test
    public void historyListsTheNewest50()
    {
        UUID oldest = battle(ash, 50, "WIN");
        battles(ash, 54, 60);

        AnalyticsSummary summary = summaryOf(ash);

        assertThat(summary.battles()).isEqualTo(55);
        assertThat(summary.history()).hasSize(50).extracting(BattleRow::matchId).doesNotContain(oldest);
    }

    // Issue #122: the summary carries the player's trend, from their own history: 15 battles at 70 then 5 at 80 is
    // +10, improving (another player's battles don't count).
    @Test
    public void theSummaryCarriesThePlayersTrend()
    {
        battles(ash, 15, 70);
        battles(misty, 5, 0);
        battles(ash, 5, 80);

        Trend trend = summaryOf(ash).trend();

        assertThat(trend).isNotNull();
        assertThat(trend.direction()).isEqualTo(Trend.Direction.IMPROVING);
        assertThat(trend.velocity()).isEqualByComparingTo("10.0");
        assertThat(trend.rolling().last10()).isEqualByComparingTo("75.0");
    }

    // Issue #123: the summary carries the player's top teams, ranked over their whole history (not just the newest 50
    // listed): Ash's 3-battle Swampert team from early on still counts; Misty's battles don't.
    @Test
    public void theSummaryCarriesThePlayersTopTeams()
    {
        battles(ash, 3, 80);
        battles(misty, 3, 99);
        for (int i = 0; i < 50; i++)
        {
            entityManager.persist(new MatchHistory(entityManager.persist(new BattleLog(UUID.randomUUID(),
                START.plus(played, ChronoUnit.MINUTES), List.of("Medicham", "Lickitung"), List.of("Azumarill"),
                BigDecimal.valueOf(50), "ace", null, 0, null, null, 0, 0, 0, 0, "{}")), ash, null,
                START.plus(played++, ChronoUnit.MINUTES)));
        }

        List<TeamRanking> top = summaryOf(ash).topTeams();

        assertThat(top).isNotNull();
        assertThat(top).extracting(TeamRanking::team).containsExactly(List.of("Swampert", "Cradily", "Clodsire"));
        assertThat(top.get(0).battles()).isEqualTo(3);
    }

    // A stored battle's full result goes to its owner only: another player gets nothing.
    @Test
    public void aBattlesResultIsOnlyForItsOwner()
    {
        UUID ashs = battle(ash, 61, "WIN");
        entityManager.flush();
        entityManager.clear();

        assertThat(analytics.battleResult(ash, ashs)).hasValueSatisfying(json -> assertThat(json).contains("\"score\""));
        assertThat(analytics.battleResult(misty, ashs)).isEmpty();
        assertThat(analytics.battleResult(ash, UUID.randomUUID())).isEmpty();
    }
}

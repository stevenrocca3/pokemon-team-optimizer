package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.analytics.Trend.Direction;
import com.pokemonoptimizer.pokemon_optimizer_backend.history.BattleRow;

/** Issue #122: improving, stable or declining, from the last 5 battles against the 15 before them. */
public class TrendEngineTest
{
    /** Mistakes in one battle: timing errors, wasted shields, bad switches, wrong charged moves. */
    private record Mistakes(int timing, int shield, int switches, int charge) {}

    private static final Mistakes CLEAN = new Mistakes(0, 0, 0, 0);

    /** Builds a history oldest first (the way it's written), handed to the engine newest first (the way it's read). */
    private static final class History
    {
        private final List<BattleRow> oldestFirst = new ArrayList<>();

        History add(int count, double score, Mistakes m)
        {
            for (int i = 0; i < count; i++)
            {
                oldestFirst.add(new BattleRow(UUID.randomUUID(), Instant.EPOCH.plusSeconds(oldestFirst.size()),
                    List.of("Swampert", "Cradily", "Clodsire"), List.of("Morpeko", "Gastrodon", "Doublade"),
                    BigDecimal.valueOf(score), "ace", "WIN", 0, null, null, m.timing(), m.shield(), m.switches(), m.charge()));
            }
            return this;
        }

        History add(int count, double score)
        {
            return add(count, score, CLEAN);
        }

        Trend trend()
        {
            return TrendEngine.trend(oldestFirst.reversed());
        }
    }

    // Rolling averages over the last 5, 10 and 25 battles, each null until there are that many. With 12 battles
    // (two at 40, then five at 60, then five at 80): last 5 = 80, last 10 = 70, last 25 not yet.
    @Test
    public void rollingAveragesOverTheLast5_10And25()
    {
        Trend.Rolling rolling = new History().add(2, 40).add(5, 60).add(5, 80).trend().rolling();

        assertThat(rolling.last5()).isEqualByComparingTo("80.0");
        assertThat(rolling.last10()).isEqualByComparingTo("70.0");
        assertThat(rolling.last25()).isNull();
    }

    // Under 10 battles there aren't 5 earlier battles to compare the last 5 against: no velocity, and the summary says
    // how many more battles it takes.
    @Test
    public void underTenBattlesThereIsNoTrendYet()
    {
        Trend trend = new History().add(9, 70).trend();

        assertThat(trend.direction()).isEqualTo(Direction.NOT_ENOUGH_DATA);
        assertThat(trend.velocity()).isNull();
        assertThat(trend.focus()).isNull();
        assertThat(trend.summary()).contains("1 more battle");
    }

    // Last 5 averaging 80 against 70 for the 15 before: velocity +10, improving.
    @Test
    public void risingScoresAreImproving()
    {
        Trend trend = new History().add(15, 70).add(5, 80).trend();

        assertThat(trend.direction()).isEqualTo(Direction.IMPROVING);
        assertThat(trend.velocity()).isEqualByComparingTo("10.0");
        assertThat(trend.recentAverage()).isEqualByComparingTo("80.0");
        assertThat(trend.earlierAverage()).isEqualByComparingTo("70.0");
    }

    // Last 5 averaging 60 against 70 before: velocity −10, declining.
    @Test
    public void fallingScoresAreDeclining()
    {
        Trend trend = new History().add(15, 70).add(5, 60).trend();

        assertThat(trend.direction()).isEqualTo(Direction.DECLINING);
        assertThat(trend.velocity()).isEqualByComparingTo("-10.0");
    }

    // The thresholds are strict (spec: over +3.0, under −3.0): exactly ±3 is stable, ±3.2 is not.
    @Test
    public void threePointsEitherWayIsStable()
    {
        assertThat(new History().add(15, 70).add(5, 73).trend().direction()).isEqualTo(Direction.STABLE);
        assertThat(new History().add(15, 70).add(5, 67).trend().direction()).isEqualTo(Direction.STABLE);
        assertThat(new History().add(15, 70).add(4, 73).add(1, 74).trend().direction()).isEqualTo(Direction.IMPROVING);
        assertThat(new History().add(15, 70).add(4, 67).add(1, 66).trend().direction()).isEqualTo(Direction.DECLINING);
    }

    // Only the 15 battles before the last 5 count as "before": ten older battles at 0 would turn this decline (60 vs
    // 70) into an improvement if they were included.
    @Test
    public void onlyTheFifteenBattlesBeforeTheLastFiveCount()
    {
        Trend trend = new History().add(10, 0).add(15, 70).add(5, 60).trend();

        assertThat(trend.earlierAverage()).isEqualByComparingTo("70.0");
        assertThat(trend.direction()).isEqualTo(Direction.DECLINING);
    }

    // With between 5 and 15 earlier battles, "before" is however many there are: 7 battles at 50, then 5 at 60.
    @Test
    public void fewerThanFifteenEarlierBattlesUseWhatThereIs()
    {
        Trend trend = new History().add(7, 50).add(5, 60).trend();

        assertThat(trend.earlierAverage()).isEqualByComparingTo("50.0");
        assertThat(trend.velocity()).isEqualByComparingTo("10.0");
    }

    // A decline names the mistake that's costing the points: here timing errors went from none to 2 a battle (24
    // points) while one more wasted shield appeared in a single battle (3 points a battle). The summary says so, with
    // the per-battle numbers.
    @Test
    public void aDeclineNamesTheMistakeCostingThePoints()
    {
        Trend trend = new History().add(15, 88).add(4, 64, new Mistakes(2, 0, 0, 0)).add(1, 49, new Mistakes(2, 1, 0, 0)).trend();

        assertThat(trend.direction()).isEqualTo(Direction.DECLINING);
        assertThat(trend.focus()).isEqualTo("timing");
        assertThat(trend.summary()).contains("timing").contains("2.0 per battle").contains("0.0");
    }

    // An improvement names the mistake that dropped away: wrong switches went from 2 a battle to none.
    @Test
    public void anImprovementNamesTheMistakeThatDroppedAway()
    {
        Trend trend = new History().add(15, 70, new Mistakes(0, 0, 2, 0)).add(5, 100).trend();

        assertThat(trend.direction()).isEqualTo(Direction.IMPROVING);
        assertThat(trend.focus()).isEqualTo("switch");
        assertThat(trend.summary()).contains("switch").contains("0.0 per battle").contains("2.0");
    }

    // The same history always gives the same trend (it's a pure function of the scores and mistakes).
    @Test
    public void theSameHistoryGivesTheSameTrend()
    {
        History history = new History().add(15, 70, new Mistakes(1, 0, 0, 0)).add(5, 58, new Mistakes(2, 0, 0, 0));

        assertThat(history.trend()).isEqualTo(history.trend());
        assertThat(history.trend().direction()).isEqualTo(Direction.DECLINING);
    }
}

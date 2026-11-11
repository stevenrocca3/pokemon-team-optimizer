package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

import com.pokemonoptimizer.pokemon_optimizer_backend.history.BattleRow;

/**
 * Performance velocity from a player's history (#122, spec §2.1): the last 5 battles' average score against the 15
 * before them. Over +3 points is improving, under −3 declining. The summary names the mistake behind the change,
 * weighed by what it costs (the match score's deductions: timing 12, shield 15, switch 15, charged move 10), so the
 * mistake named is the one that moved the score most, not just the one that happened most.
 */
public final class TrendEngine
{
    static final int RECENT = 5;
    static final int EARLIER = 15;
    static final BigDecimal THRESHOLD = BigDecimal.valueOf(3);

    private record Category(String key, String label, int cost, ToIntFunction<BattleRow> count) {}

    private static final List<Category> CATEGORIES = List.of(
        new Category("timing", "timing errors (charged moves thrown on the opponent's alignment)", 12, BattleRow::timingErrors),
        new Category("shield", "wasted shields", 15, BattleRow::mismanagedShields),
        new Category("switch", "switch mistakes (slow switches and missed catches)", 15, BattleRow::suboptimalSwitches),
        new Category("charge", "wrong charged move choices", 10, BattleRow::suboptimalCharges));

    private TrendEngine() {}

    public static Trend trend(List<BattleRow> newestFirst)
    {
        Trend.Rolling rolling = new Trend.Rolling(average(newestFirst, 5), average(newestFirst, 10), average(newestFirst, 25));
        int battles = newestFirst.size();
        if (battles < RECENT * 2)
        {
            int more = RECENT * 2 - battles;
            return new Trend(Trend.Direction.NOT_ENOUGH_DATA, null, null, null, rolling, null,
                "Trends start at " + RECENT * 2 + " battles: " + more + " more battle" + (more == 1 ? "" : "s") + " to go.");
        }

        List<BattleRow> recent = newestFirst.subList(0, RECENT);
        List<BattleRow> earlier = newestFirst.subList(RECENT, Math.min(battles, RECENT + EARLIER));
        BigDecimal recentAverage = mean(recent);
        BigDecimal earlierAverage = mean(earlier);
        BigDecimal velocity = recentAverage.subtract(earlierAverage);
        Trend.Direction direction = velocity.compareTo(THRESHOLD) > 0 ? Trend.Direction.IMPROVING
            : velocity.compareTo(THRESHOLD.negate()) < 0 ? Trend.Direction.DECLINING
            : Trend.Direction.STABLE;

        // how many points a battle each category moved the score by (positive: it cost more recently)
        Optional<Category> focus = switch (direction)
        {
            case DECLINING -> mostMoved(recent, earlier, 1);
            case IMPROVING -> mostMoved(recent, earlier, -1);
            default -> costliest(recent);
        };

        String averages = "your last " + RECENT + " battles average " + one(recentAverage) + ", against "
            + one(earlierAverage) + " in the " + earlier.size() + " before.";
        String summary = switch (direction)
        {
            case DECLINING -> "Down " + one(velocity.abs()) + " points: " + averages
                + focus.map(c -> " Biggest cause: " + change(c, recent, earlier)).orElse("");
            case IMPROVING -> "Up " + one(velocity) + " points: " + averages
                + focus.map(c -> " Biggest gain: " + change(c, recent, earlier)).orElse("");
            default -> "Holding steady: " + averages
                + focus.map(c -> " Most points still go to " + c.label() + ", " + one(rate(recent, c)) + " per battle.").orElse("");
        };
        return new Trend(direction, velocity.setScale(1, RoundingMode.HALF_UP), recentAverage.setScale(1, RoundingMode.HALF_UP),
            earlierAverage.setScale(1, RoundingMode.HALF_UP), rolling, focus.map(Category::key).orElse(null), summary);
    }

    /** The category whose cost per battle moved most in `sign`'s direction (1: rose recently, −1: fell), if any moved. */
    private static Optional<Category> mostMoved(List<BattleRow> recent, List<BattleRow> earlier, int sign)
    {
        return CATEGORIES.stream()
            .filter(c -> pointsMoved(c, recent, earlier) * sign > 0)
            .max(Comparator.comparingDouble(c -> pointsMoved(c, recent, earlier) * sign));
    }

    /** Holding steady: the category costing the most points in the last 5, if any mistakes were made. */
    private static Optional<Category> costliest(List<BattleRow> recent)
    {
        return CATEGORIES.stream()
            .filter(c -> rate(recent, c).signum() > 0)
            .max(Comparator.comparingDouble(c -> rate(recent, c).doubleValue() * c.cost()));
    }

    private static double pointsMoved(Category c, List<BattleRow> recent, List<BattleRow> earlier)
    {
        return (rate(recent, c).doubleValue() - rate(earlier, c).doubleValue()) * c.cost();
    }

    private static String change(Category c, List<BattleRow> recent, List<BattleRow> earlier)
    {
        return c.label() + ", " + one(rate(recent, c)) + " per battle now against " + one(rate(earlier, c)) + " before.";
    }

    private static BigDecimal rate(List<BattleRow> rows, Category c)
    {
        int total = rows.stream().mapToInt(c.count()).sum();
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(rows.size()), 4, RoundingMode.HALF_UP);
    }

    /** The newest `n` battles' average score, or null with fewer than `n`. */
    private static BigDecimal average(List<BattleRow> newestFirst, int n)
    {
        return newestFirst.size() < n ? null : mean(newestFirst.subList(0, n)).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal mean(List<BattleRow> rows)
    {
        BigDecimal total = rows.stream().map(BattleRow::score).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(rows.size()), 4, RoundingMode.HALF_UP);
    }

    private static String one(BigDecimal value)
    {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}

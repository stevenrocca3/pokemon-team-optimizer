package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;

import org.junit.jupiter.api.Test;

public class RatingSummaryTest
{
    @Test
    public void percentagesAndAverageComeFromTheCounts()
    {   // 3 fives and 1 four: (15 + 4) / 4 = 4.75
        RatingSummary summary = RatingSummary.from(List.of(new StarCount(5, 3L), new StarCount(4, 1L)));

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.average()).isEqualTo(4.75);
        assertThat(summary.percentByStars()).containsExactly(
            entry(5, 75.0), entry(4, 25.0), entry(3, 0.0), entry(2, 0.0), entry(1, 0.0));
    }

    @Test
    public void noRatingsYetIsAllZeroNotNaN()
    {   // 0 / 0 would be NaN, which isn't a valid JSON number
        RatingSummary summary = RatingSummary.from(List.of());

        assertThat(summary.average()).isEqualTo(0.0);
        assertThat(summary.percentByStars()).containsExactly(
            entry(5, 0.0), entry(4, 0.0), entry(3, 0.0), entry(2, 0.0), entry(1, 0.0));
    }

    @Test
    public void percentagesRoundToOneDecimal()
    {   // 2 of 3 is 66.666…%, 1 of 3 is 33.333…%
        RatingSummary summary = RatingSummary.from(List.of(new StarCount(5, 2L), new StarCount(1, 1L)));

        assertThat(summary.percentByStars()).containsEntry(5, 66.7).containsEntry(1, 33.3);
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.dto;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The rating split for tracking: percent per star level (5 down to 1), always all five. */
public record RatingSummary(long total, double average, Map<Integer, Double> percentByStars)
{
    public static RatingSummary from(List<StarCount> counts)
    {
        long total = 0;
        long starSum = 0;
        Map<Integer, Long> byStars = new HashMap<>();
        for (StarCount c : counts)
        {
            total += c.count();
            starSum += c.stars() * c.count();
            byStars.put(c.stars(), c.count());
        }

        Map<Integer, Double> percent = new LinkedHashMap<>();   // keeps 5 → 1 order in the JSON
        for (int stars = 5; stars >= 1; stars--)
        {
            long n = byStars.getOrDefault(stars, 0L);
            percent.put(stars, total == 0 ? 0.0 : Math.round(n * 1000.0 / total) / 10.0);
        }
        // no ratings yet: 0, not 0 / 0 = NaN
        double average = total == 0 ? 0.0 : Math.round(starSum * 100.0 / total) / 100.0;
        return new RatingSummary(total, average, percent);
    }
}

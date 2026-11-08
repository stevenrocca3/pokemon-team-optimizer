package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.RatingRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.RatingSummary;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Rating;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.RatingRepository;

import jakarta.validation.Valid;

/**
 * The star rating in the feedback dialog. The summary is public on purpose: it's only aggregate percentages.
 * Split by page with: SELECT page, stars, COUNT(*) FROM rating GROUP BY page, stars;
 */
@RestController
@RequestMapping("/api/ratings")
public class RatingController
{
    private final RatingRepository ratingRepository;

    public RatingController(RatingRepository ratingRepository)
    {
        this.ratingRepository = ratingRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void rate(@Valid @RequestBody RatingRequest request)
    {
        ratingRepository.save(new Rating(request.stars(), request.page(), Instant.now()));
    }

    @GetMapping("/summary")
    public RatingSummary summary()
    {
        return RatingSummary.from(ratingRepository.countByStars());
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.FeedbackRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Feedback;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.FeedbackRepository;

import jakarta.validation.Valid;

/** The feedback box on every page. Read it with: SELECT * FROM feedback ORDER BY created_at DESC; */
@RestController
@RequestMapping("/api")
public class FeedbackController
{
    private final FeedbackRepository feedbackRepository;

    public FeedbackController(FeedbackRepository feedbackRepository)
    {
        this.feedbackRepository = feedbackRepository;
    }

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public void send(@Valid @RequestBody FeedbackRequest request)
    {
        feedbackRepository.save(new Feedback(request.message().strip(), request.page(), Instant.now()));
    }
}

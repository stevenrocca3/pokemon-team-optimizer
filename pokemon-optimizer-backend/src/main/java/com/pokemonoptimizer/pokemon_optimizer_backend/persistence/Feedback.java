package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Feedback
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String message;
    private String page;
    private Instant createdAt;

    protected Feedback() {}

    public Feedback(String message, String page, Instant createdAt)
    {
        this.message = message;
        this.page = page;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getMessage() { return message; }
    public String getPage() { return page; }
    public Instant getCreatedAt() { return createdAt; }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** One star rating from the feedback dialog. Anonymous: no Trainer is stored. */
@Entity
public class Rating
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private int stars;
    private String page;
    private Instant createdAt;

    protected Rating() {}

    public Rating(int stars, String page, Instant createdAt)
    {
        this.stars = stars;
        this.page = page;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public int getStars() { return stars; }
    public String getPage() { return page; }
    public Instant getCreatedAt() { return createdAt; }
}

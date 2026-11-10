package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Only the Google ID, which ties a login to its Collection. The name stays with the login session (Google is never
 * asked for the email), so there's nothing personal here to leak or keep up to date.
 */
@Entity
public class Trainer
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String googleId;
    protected Trainer(){}

    public Trainer(String googleId)
    {
        this.googleId = googleId;
    }

    public Long getId()
    {
        return id;
    }
}

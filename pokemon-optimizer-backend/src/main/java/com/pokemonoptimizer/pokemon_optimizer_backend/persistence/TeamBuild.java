package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;

import java.time.Instant;

import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** One finished Team Builder run, counted for the "teams built" total. Anonymous: no Trainer, no Pokémon, no IP. */
@Entity
public class TeamBuild
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private League league;
    private String cup;
    private int pokemonCount;
    private Instant createdAt;

    protected TeamBuild() {}

    public TeamBuild(League league, String cup, int pokemonCount, Instant createdAt)
    {
        this.league = league;
        this.cup = cup;
        this.pokemonCount = pokemonCount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public League getLeague() { return league; }
    public String getCup() { return cup; }
    public int getPokemonCount() { return pokemonCount; }
    public Instant getCreatedAt() { return createdAt; }
}

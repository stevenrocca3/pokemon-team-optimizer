package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.StarCount;

public interface RatingRepository extends JpaRepository<Rating, Long>
{
    /** How many ratings each star level has, counted by the database; levels nobody picked are missing. */
    @Query("SELECT new com.pokemonoptimizer.pokemon_optimizer_backend.dto.StarCount(r.stars, COUNT(r)) FROM Rating r GROUP BY r.stars")
    List<StarCount> countByStars();
}

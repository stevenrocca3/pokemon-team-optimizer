package com.pokemonoptimizer.pokemon_optimizer_backend.controller;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemon;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemonRepository;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/api")
public class CaughtPokemonController 
{

    private final CaughtPokemonRepository caughtPokemonRepository;
    private final SpeciesLookup speciesLookup;
    public CaughtPokemonController(CaughtPokemonRepository cpr, SpeciesLookup sl)
    {
        caughtPokemonRepository = cpr;
        speciesLookup = sl;
    }
    

    @GetMapping("/collection")
    public List<CaughtPokemon> getCollection()
    {
        return caughtPokemonRepository.findAll();
    }
    @PostMapping("/collection")
    @ResponseStatus(HttpStatus.CREATED)
    public CaughtPokemon addToCollection(@Valid @RequestBody CaughtPokemonRequest request) 
    {
        if (speciesLookup.findById(request.speciesId()).isEmpty()) 
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Species: " + request.speciesId() + " Not Found.");
        }
        boolean isWholeOrHalf = request.level() * 2 == Math.floor(request.level() * 2);
        if (!isWholeOrHalf) 
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Level: " + request.level() + " must be a whole or half #.");
        }
        CaughtPokemon cp = new CaughtPokemon(request.speciesId(), request.atkIv(), request.defIv(), request.staIv(), request.level());
        return caughtPokemonRepository.save(cp);
    }

    @DeleteMapping("/collection/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFromCollection(@PathVariable Long id)
    {
        if (!caughtPokemonRepository.existsById(id)) 
        {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Caught Pokemon: " + id + " Not Found.");
        }
        caughtPokemonRepository.deleteById(id);
    }
}

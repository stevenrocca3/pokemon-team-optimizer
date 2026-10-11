package com.pokemonoptimizer.pokemon_optimizer_backend;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.jpa.repository.JpaRepository; 
import org.springframework.http.HttpStatus;
import java.util.List;
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

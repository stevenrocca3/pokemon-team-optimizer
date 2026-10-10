package com.pokemonoptimizer.pokemon_optimizer_backend;

import org.springframework.web.bind.annotation.*; 
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CaughtPokemonController 
{

    private final CaughtPokemonRepository caughtPokemonRepository;
    public CaughtPokemonController(CaughtPokemonRepository cpr)
    {
        caughtPokemonRepository = cpr;
    }

    @GetMapping("/collection")
    public List<CaughtPokemon> getCollection()
    {
        return caughtPokemonRepository.findAll();
    }
    @PostMapping("/collection")
    @ResponseStatus(HttpStatus.CREATED)
    public CaughtPokemon addToCollection(@RequestBody CaughtPokemonRequest request) 
    {
        CaughtPokemon cp = new CaughtPokemon(request.speciesId(), request.atkIv(), request.defIv(), request.staIv(), request.level());
        return caughtPokemonRepository.save(cp);
    }
}

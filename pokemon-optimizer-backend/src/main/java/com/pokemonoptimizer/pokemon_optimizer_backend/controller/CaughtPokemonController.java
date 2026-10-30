package com.pokemonoptimizer.pokemon_optimizer_backend.controller;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestParam;



@RestController
@RequestMapping("/api")
public class CaughtPokemonController 
{

    private final CaughtPokemonRepository caughtPokemonRepository;
    private final TrainerRepository trainerRepository;
    private final SpeciesLookup speciesLookup;
    public CaughtPokemonController(CaughtPokemonRepository cpr, SpeciesLookup sl, TrainerRepository tr)
    {
        caughtPokemonRepository = cpr;
        speciesLookup = sl;
        trainerRepository = tr;
    }

    private Trainer currentTrainer(OidcUser user)
    {
        return trainerRepository.findByGoogleId(user.getSubject()).orElseThrow();
    }

    @GetMapping("/collection")
    public List<CaughtPokemon> getCollection(@AuthenticationPrincipal OidcUser user)
    {
        return caughtPokemonRepository.findByTrainer(currentTrainer(user));
    }
    @PostMapping("/collection")
    @ResponseStatus(HttpStatus.CREATED)
    public CaughtPokemon addToCollection(@Valid @RequestBody CaughtPokemonRequest request, @AuthenticationPrincipal OidcUser user) 
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
        CaughtPokemon cp = new CaughtPokemon(request.speciesId(), request.atkIv(), request.defIv(), request.staIv(), request.level(), currentTrainer(user));
        return caughtPokemonRepository.save(cp);
    }

    @DeleteMapping("/collection/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFromCollection(@PathVariable Long id, @AuthenticationPrincipal OidcUser user)
    {
        CaughtPokemon cp = caughtPokemonRepository.findByIdAndTrainer(id, currentTrainer(user))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caught Pokemon: " + id + "Not Found."));
        caughtPokemonRepository.delete(cp);
    }
}

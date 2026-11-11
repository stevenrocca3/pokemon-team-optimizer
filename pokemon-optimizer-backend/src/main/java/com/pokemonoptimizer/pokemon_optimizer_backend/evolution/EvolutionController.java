package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

/** Public and stateless, like the IV rankings: guests project the Pokémon they typed in, accounts their saved ones. */
@RestController
@RequestMapping("/api")
public class EvolutionController
{
    private final EvolutionProjector projector;

    public EvolutionController(EvolutionProjector projector)
    {
        this.projector = projector;
    }

    @PostMapping("/evolutions")
    public EvolutionProjection project(@Valid @RequestBody EvolutionRequest request)
    {
        return projector.find(request.speciesId(), request.atkIv(), request.defIv(), request.staIv(), request.level(), request.shadow())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No species called " + request.speciesId() + "."));
    }
}

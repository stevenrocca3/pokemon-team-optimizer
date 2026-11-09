package com.pokemonoptimizer.pokemon_optimizer_backend.controller;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonBatch;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;
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

    /** The Team Builder's input limit (TeamBuilderRequest): a Collection can always be built from in one go. */
    static final int MAX_COLLECTION = 10_000;

    private static ResponseStatusException full(int room)
    {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, room == 0
            ? "Your Collection is full (10,000 Pokémon). Remove some to add more."
            : "A Collection holds up to 10,000 Pokémon, and yours has room for " + room + " more.");
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
        String problem = problemWith(request);
        if (problem != null)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
        }
        Trainer trainer = currentTrainer(user);
        if (caughtPokemonRepository.countByTrainer(trainer) >= MAX_COLLECTION)
        {
            throw full(0);
        }
        return caughtPokemonRepository.save(toEntity(request, trainer));
    }

    @PostMapping("/collection/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CaughtPokemon> addAllToCollection(@Valid @RequestBody CaughtPokemonBatch batch, @AuthenticationPrincipal OidcUser user)
    {
        // check everything before saving anything, so a bad row never leaves half an import behind
        List<CaughtPokemonRequest> requests = batch.pokemon();
        for (int i = 0; i < requests.size(); i++)
        {
            String problem = problemWith(requests.get(i));
            if (problem != null)
            {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pokémon #" + (i + 1) + ": " + problem);
            }
        }
        Trainer trainer = currentTrainer(user);

        // Importing the same export twice must not double the Collection. Same species, IVs and shadow
        // means the same Pokémon: two identical ones are very rare and couldn't share a team anyway.
        Map<String, CaughtPokemon> owned = new HashMap<>();
        List<CaughtPokemon> alreadyOwned = caughtPokemonRepository.findByTrainer(trainer);
        for (CaughtPokemon pokemon : alreadyOwned)
        {
            owned.putIfAbsent(sameness(pokemon.getSpeciesId(), pokemon.getAtkIv(), pokemon.getDefIv(), pokemon.getStaIv(), pokemon.isShadow()), pokemon);
        }
        Map<String, CaughtPokemon> toSave = new LinkedHashMap<>();
        for (CaughtPokemonRequest request : requests)
        {
            String key = sameness(request.speciesId(), request.atkIv(), request.defIv(), request.staIv(), request.shadow());
            CaughtPokemon match = owned.get(key);
            if (match == null)
            {
                CaughtPokemon added = toEntity(request, trainer);
                owned.put(key, added);
                toSave.put(key, added);
            }
            else if (request.level() > match.getLevel())
            {
                match.raiseLevelTo(request.level());
                toSave.put(key, match);
            }
        }
        long added = toSave.values().stream().filter(p -> p.getId() == null).count();
        int room = Math.max(0, MAX_COLLECTION - alreadyOwned.size());
        if (added > room)
        {   // all or nothing, like a bad row: a half-saved import is harder to make sense of than a clear refusal
            throw full(room);
        }
        return caughtPokemonRepository.saveAll(new ArrayList<>(toSave.values()));
    }

    private static String sameness(String speciesId, int atkIv, int defIv, int staIv, boolean shadow)
    {
        return speciesId + "|" + atkIv + "|" + defIv + "|" + staIv + "|" + shadow;
    }

    /** Why this Pokémon can't exist in the game, or null if it can. */
    private String problemWith(CaughtPokemonRequest request)
    {
        Optional<SpeciesType> found = speciesLookup.findById(request.speciesId());
        if (found.isEmpty())
        {
            return "Species: " + request.speciesId() + " Not Found.";
        }
        SpeciesType species = found.get();
        if (!species.learnset().allows(request.fastMove(), request.chargedMoves()))
        {
            return "Learnset: " + request.fastMove() + request.chargedMoves() + " Not Found.";
        }
        if (request.shadow() && !species.shadowEligible())
        {
            return "Shadow: " + species.speciesName() + " isn't shadow eligible.";
        }
        boolean isWholeOrHalf = request.level() * 2 == Math.floor(request.level() * 2);
        if (!isWholeOrHalf)
        {
            return "Level: " + request.level() + " must be a whole or half #.";
        }
        return null;
    }

    private static CaughtPokemon toEntity(CaughtPokemonRequest request, Trainer trainer)
    {
        return new CaughtPokemon(request.speciesId(), request.atkIv(), request.defIv(), request.staIv(), request.level(),
            request.shadow(), request.fastMove(), request.chargedMoves(), trainer);
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

package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemon;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemonRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.MetaPresets;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * "Add to Box" on a Team Builder suggestion (#114): saves the meta Pokémon to the Trainer's Collection as PvPoke builds
 * it, worked out here from the meta, not taken from the browser. Logged in only (SecurityConfig: /api/collection/**);
 * a guest's browser keeps the suggestion's own build. 201 when added, 200 with the one already there.
 */
@RestController
@RequestMapping("/api/collection")
public class MetaPresetController
{
    record PresetRequest(@NotBlank @Size(max = 64) String speciesId, Boolean shadow, @NotNull League league, @Size(max = 128) String cup) {}

    private final MetaPresets presets;
    private final CaughtPokemonRepository collection;
    private final TrainerRepository trainers;

    public MetaPresetController(MetaPresets presets, CaughtPokemonRepository collection, TrainerRepository trainers)
    {
        this.presets = presets;
        this.collection = collection;
        this.trainers = trainers;
    }

    @PostMapping("/meta-preset")
    public ResponseEntity<CaughtPokemon> add(@Valid @RequestBody PresetRequest request, @AuthenticationPrincipal OidcUser user)
    {
        boolean shadow = Boolean.TRUE.equals(request.shadow());
        CaughtPokemonRequest preset = presets.preset(request.league(), request.cup(), request.speciesId(), shadow)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, request.speciesId() + " isn't in that meta"));
        Trainer trainer = trainers.findByGoogleId(user.getSubject()).orElseThrow();

        // same species, IVs and Shadow is the same Pokémon (CaughtPokemonController's rule)
        for (CaughtPokemon owned : collection.findByTrainer(trainer))
        {
            if (owned.getSpeciesId().equals(preset.speciesId()) && owned.getAtkIv() == preset.atkIv() && owned.getDefIv() == preset.defIv()
                && owned.getStaIv() == preset.staIv() && owned.isShadow() == preset.shadow())
            {
                return ResponseEntity.ok(owned);
            }
        }
        if (collection.countByTrainer(trainer) >= CaughtPokemonController.MAX_COLLECTION)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your Collection is full (10,000 Pokémon). Remove some to add more.");
        }
        CaughtPokemon saved = collection.save(new CaughtPokemon(preset.speciesId(), preset.atkIv(), preset.defIv(), preset.staIv(),
            preset.level(), preset.shadow(), preset.fastMove(), preset.chargedMoves(), trainer));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}

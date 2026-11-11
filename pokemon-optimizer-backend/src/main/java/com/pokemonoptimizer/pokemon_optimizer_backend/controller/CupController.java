package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.pokemonoptimizer.pokemon_optimizer_backend.cups.Cup;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.CupCatalog;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CupSummary;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.MetaPick;

/** The live Cups from PvPoke's data, and each one's meta for the checklist's Cups tab (#117), with types. */
@RestController
@RequestMapping("/api")
public class CupController
{
    private final CupCatalog cups;
    private final SpeciesLookup species;

    public CupController(CupCatalog cups, SpeciesLookup species)
    {
        this.cups = cups;
        this.species = species;
    }

    @GetMapping("/cups")
    public List<CupSummary> cups()
    {
        return cups.cups().stream()
            .map(cup -> new CupSummary(cup.key(), cup.title(), cup.league(), cup.partySize(), cup.rules(), cup.meta().size(), cup.updated()))
            .toList();
    }

    @GetMapping("/meta/cup/{key}")
    public List<MetaPick> meta(@PathVariable String key)
    {
        Cup cup = cups.find(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That Cup isn't running any more"));
        // the Cup was built from the species data, so every one of its Pokémon is in it
        return cup.meta().stream()
            .map(m -> OwnedSpecies.ofPvpokeId(m.id()))
            .flatMap(owned -> species.findById(owned.speciesId()).map(s -> MetaPick.of(owned, s)).stream())
            .toList();
    }
}

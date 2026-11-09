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

/** The live Cups from PvPoke's data, and each one's meta for the checklist. */
@RestController
@RequestMapping("/api")
public class CupController
{
    private final CupCatalog cups;

    public CupController(CupCatalog cups)
    {
        this.cups = cups;
    }

    @GetMapping("/cups")
    public List<CupSummary> cups()
    {
        return cups.cups().stream()
            .map(cup -> new CupSummary(cup.key(), cup.title(), cup.league(), cup.partySize(), cup.rules(), cup.meta().size(), cup.updated()))
            .toList();
    }

    @GetMapping("/meta/cup/{key}")
    public List<OwnedSpecies> meta(@PathVariable String key)
    {
        Cup cup = cups.find(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That Cup isn't running any more"));
        return cup.meta().stream().map(m -> OwnedSpecies.ofPvpokeId(m.id())).toList();
    }
}

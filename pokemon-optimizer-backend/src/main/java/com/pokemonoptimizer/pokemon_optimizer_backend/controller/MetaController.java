package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.MetaLookup;

/** The Collection checklist's default list per League: the meta the Team Builder battles against. */
@RestController
@RequestMapping("/api")
public class MetaController
{
    private static final String SHADOW_SUFFIX = "_shadow";

    private final MetaLookup metaLookup;

    public MetaController(MetaLookup metaLookup)
    {
        this.metaLookup = metaLookup;
    }

    /** PvPoke's ids ("annihilape_shadow") split into species and Shadow, in the meta's order. */
    @GetMapping("/meta/{league}")
    public List<OwnedSpecies> meta(@PathVariable League league)
    {
        return metaLookup.meta(league).stream().map(m -> m.id().endsWith(SHADOW_SUFFIX)
            ? new OwnedSpecies(m.id().substring(0, m.id().length() - SHADOW_SUFFIX.length()), true)
            : new OwnedSpecies(m.id(), false)).toList();
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.MetaLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.MetaPick;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.MetaRankings;

/** Each League's meta: the one the Team Builder battles against, and PvPoke's top-ranked list for the checklist. */
@RestController
@RequestMapping("/api")
public class MetaController
{
    private final MetaLookup metaLookup;
    private final MetaRankings rankings;

    public MetaController(MetaLookup metaLookup, MetaRankings rankings)
    {
        this.metaLookup = metaLookup;
        this.rankings = rankings;
    }

    /** PvPoke's ids ("annihilape_shadow") split into species and Shadow, in the meta's order. */
    @GetMapping("/meta/{league}")
    public List<OwnedSpecies> meta(@PathVariable League league)
    {
        return metaLookup.meta(league).stream().map(m -> OwnedSpecies.ofPvpokeId(m.id())).toList();
    }

    /** The checklist's quick-select grid (#116): PvPoke's top 150 (Great, Ultra) or 50 (Master), best first. */
    @GetMapping("/meta/{league}/top")
    public List<MetaPick> top(@PathVariable League league)
    {
        return rankings.top(league);
    }
}

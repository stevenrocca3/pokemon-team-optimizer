package com.pokemonoptimizer.pokemon_optimizer_backend.controller;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.SpeciesDetail;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.SpeciesOption;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;


@RestController 
@RequestMapping("/api")
public class SpeciesController 
{
    private final SpeciesLookup speciesLookup;

    public SpeciesController(SpeciesLookup sl)
    {
       speciesLookup = sl;
    }

    @GetMapping("/species")
    public List<SpeciesOption> speciesOption() {
        ArrayList<SpeciesOption> speciesOptionList = new ArrayList<>();
        for (SpeciesType species : speciesLookup.findAll())
        {
            SpeciesOption speciesOption = new SpeciesOption(species.speciesId(),species.speciesName(), species.dex());
            speciesOptionList.add(speciesOption);
            
        }
        return speciesOptionList;
    }
    @GetMapping("/species/{id}")
    public SpeciesDetail speciesById(@PathVariable String id)
    {
        SpeciesType speciesType = speciesLookup.findById(id).orElseThrow(() 
        -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Species: " + id + " Not Found."));
        SpeciesDetail speciesDetail = new SpeciesDetail(speciesType.speciesId(), 
                                                        speciesType.speciesName(), speciesType.dex(), 
                                                        speciesType.baseAtk(), speciesType.baseDef(), 
                                                        speciesType.baseSta(), speciesType.learnset());
        return speciesDetail;
    }
    
}

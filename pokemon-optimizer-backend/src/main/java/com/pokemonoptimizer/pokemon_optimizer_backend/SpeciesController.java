package com.pokemonoptimizer.pokemon_optimizer_backend;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;


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
                                                        speciesType.baseSta());
        return speciesDetail;
    }
    
}

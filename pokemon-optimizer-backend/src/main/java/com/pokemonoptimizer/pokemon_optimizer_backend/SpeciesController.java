package com.pokemonoptimizer.pokemon_optimizer_backend;

import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;


@RestController 
@CrossOrigin(origins = "http://localhost:5173")
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
            SpeciesOption speciesOption = new SpeciesOption(speciesLookup.getId(species.dexNumber(), species.form()),species.name(), species.form());
            speciesOptionList.add(speciesOption);
            
        }
        return speciesOptionList;
        
    }
    
}

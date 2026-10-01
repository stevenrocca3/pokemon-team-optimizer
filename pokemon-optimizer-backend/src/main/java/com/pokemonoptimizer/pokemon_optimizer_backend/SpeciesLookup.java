package com.pokemonoptimizer.pokemon_optimizer_backend;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.List;

@Component
public class SpeciesLookup
{
    private final HashMap<String, SpeciesType> speciesById;
    private HashMap<String, SpeciesType> loadSpecies() 
    {
        HashMap<String, SpeciesType> data = new HashMap<>();
        String speciesCSV = "/pokemon_base_stats_all.csv";
        InputStream is = SpeciesLookup.class.getResourceAsStream(speciesCSV);
        if (is == null)
        {
            throw new IllegalStateException(speciesCSV + " could not be found");
        }
        try (InputStreamReader isr = new InputStreamReader(is);
            CSVReader cr = new CSVReader(isr);){
            cr.readNext();
            String[] nextRecord;
            while ((nextRecord = cr.readNext()) != null)
                {
                    int dexNumber = Integer.parseInt(nextRecord[0]);
                    String name = nextRecord[1];
                    String form = nextRecord[2];
                    int baseSta = Integer.parseInt(nextRecord[3]);
                    int baseAtk = Integer.parseInt(nextRecord[4]);
                    int baseDef = Integer.parseInt(nextRecord[5]);
                    String key = getId(dexNumber,form);
                    SpeciesType species = new SpeciesType(dexNumber, name, form, baseSta, baseAtk, baseDef);
                    if (!data.containsKey(key))
                    {
                        data.put(key, species);
                    }
                    else
                    {
                        throw new IllegalStateException("key " + key + " appears twice.");
                    } 
                }
            return data;


        } catch (IOException | CsvValidationException e)
        {
            throw new IllegalStateException("The species data couldn't be loaded", e);
        }
        
    }

    public Optional<SpeciesType> findById(String id)
    {
        return Optional.ofNullable(speciesById.get(id));
        
    }

    public List<SpeciesType> findAll()
    {
        return List.copyOf(speciesById.values());
    }

    public SpeciesLookup()
    {
        speciesById = loadSpecies();
    }

    public String getId(int dexNumber, String form)
    {
        form = form.toLowerCase();
        if (form.equals(""))
        {
            String id = Integer.toString(dexNumber);
            return id;
        }
        else
        {
            String id = dexNumber + "-" + form;
            return id;
        }
    }
}

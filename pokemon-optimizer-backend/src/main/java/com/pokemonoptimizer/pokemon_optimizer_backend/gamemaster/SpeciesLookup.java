package com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;


import java.util.HashMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.List; 
import java.util.HashSet;
import java.util.Set;

@Component
public class SpeciesLookup
{
    private final HashMap<String, SpeciesTraits> traitsById = new HashMap<>();
    /** Every non-shadow form's evolution family, unreleased ones included, so a line can be walked through them. */
    private final HashMap<String, Family> familyById = new HashMap<>();
    private final HashMap<String, SpeciesType> speciesById;
    private final HashMap<String, SpeciesType> unreleasedById = new HashMap<>();
    private HashMap<String, SpeciesType> loadSpecies() 
    {
        HashMap<String, SpeciesType> data = new HashMap<>();
        String json = "/gamemaster.json";
        try (InputStream is = SpeciesLookup.class.getResourceAsStream(json))
        {
            if (is == null)
            {
            throw new IllegalStateException(json + " could not be found");
            }
            JsonMapper mapper = new JsonMapper();
            Gamemaster gamemaster = mapper.readValue(is, Gamemaster.class); 
            for (GamemasterPokemon pokemon : gamemaster.pokemon())
                {
                    if (pokemon.tags().contains("shadow"))
                    {
                        continue;
                    }
                    
                    if (pokemon.family() != null)
                    {
                        familyById.put(pokemon.speciesId(), pokemon.family());
                    }
                    int dex = pokemon.dex();
                    String speciesName = pokemon.speciesName();
                    String speciesId = pokemon.speciesId();
                    int baseSta = pokemon.baseStats().hp();
                    int baseAtk = pokemon.baseStats().atk();
                    int baseDef = pokemon.baseStats().def();
                    //pvpoke splits these into legacy and elite moves, we do just elite moves
                    HashSet<String> eliteMoves = new HashSet<>();
                    eliteMoves.addAll(pokemon.legacyMoves());
                    eliteMoves.addAll(pokemon.eliteMoves());
                    Set<String> finalEliteMoves = Set.copyOf(eliteMoves);
                    Learnset learnset = new Learnset(pokemon.fastMoves(), pokemon.chargedMoves(), finalEliteMoves);
                    boolean shadowEligible = pokemon.tags().contains("shadoweligible");
                    SpeciesType species = new SpeciesType(dex, speciesName, speciesId, baseSta, baseAtk, baseDef, learnset, pokemon.types(), shadowEligible);
                    if (!pokemon.released())
                    {
                        unreleasedById.put(speciesId, species);   // forms only reached mid-battle, like Aegislash (Blade)
                        continue;
                    }
                    if (!data.containsKey(speciesId))
                    {
                        data.put(speciesId, species);
                        traitsById.put(speciesId, new SpeciesTraits(List.copyOf(pokemon.tags()), evolutionStage(pokemon.family())));
                    }
                    else
                    {
                         throw new IllegalStateException("key " + speciesId + " appears twice.");
                    } 
                }
                return data;
        } 
        catch (IOException | JacksonException e)
        {
            throw new IllegalStateException("The species data couldn't be loaded", e);
        }
          
    }

    /** Any form in the gamemaster, unreleased ones included (Aegislash's Blade form only exists mid-battle). */
    public Optional<SpeciesType> findForm(String id)
    {
        SpeciesType released = speciesById.get(id);
        return Optional.ofNullable(released != null ? released : unreleasedById.get(id));
    }

    public Optional<SpeciesType> findById(String id)
    {
        return Optional.ofNullable(speciesById.get(id));
        
    }

    /** Tags and evolution stage for Cup rules; empty for an unknown species. */
    public Optional<SpeciesTraits> traits(String id)
    {
        return Optional.ofNullable(traitsById.get(id));
    }

    /** The evolution family of any non-shadow form, unreleased ones included; empty when it has none. */
    public Optional<Family> family(String id)
    {
        return Optional.ofNullable(familyById.get(id));
    }

    /** PvPoke's Pokemon.getEvolutionStage. */
    private static int evolutionStage(Family family)
    {
        if (family == null)
        {
            return 0;
        }
        boolean evolves = family.evolutions() != null && !family.evolutions().isEmpty();
        boolean hasParent = family.parent() != null;
        if (evolves)
        {
            return hasParent ? 2 : 1;
        }
        return hasParent ? 3 : 0;
    }

    public List<SpeciesType> findAll()
    {
        return List.copyOf(speciesById.values());
    }

    public SpeciesLookup()
    {
        speciesById = loadSpecies();
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;


import java.util.HashMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.List;

@Component
public class MoveLookup 
{
    private final HashMap<String, Move> movesById;
    private HashMap<String, Move> loadMoves() 
    {
        HashMap<String, Move> data = new HashMap<>();
        String json = "/gamemaster.json";
        try (InputStream is = MoveLookup.class.getResourceAsStream(json))
        {
            if (is == null)
            {
            throw new IllegalStateException(json + " could not be found");
            }
            JsonMapper mapper = new JsonMapper();
            Gamemaster gamemaster = mapper.readValue(is, Gamemaster.class); 
            for (GamemasterMove move : gamemaster.moves())
                {
                    String moveId = move.moveId();
                    String name = move.name();
                    Move moves = new Move(moveId, name);
                    if (!data.containsKey(moveId))
                    {
                        data.put(moveId, moves);
                    }
                    else
                    {
                         throw new IllegalStateException("key " + moveId + " appears twice.");
                    } 
                }
                return data;
        } 
        catch (IOException | JacksonException e)
        {
            throw new IllegalStateException("The move data couldn't be loaded", e);
        }
    }

    public Optional<Move> findById(String id)
    {
        return Optional.ofNullable(movesById.get(id));
        
    }

    public List<Move> findAll()
    {
        return List.copyOf(movesById.values());
    }

    public MoveLookup()
    {
        movesById = loadMoves();
    }

}

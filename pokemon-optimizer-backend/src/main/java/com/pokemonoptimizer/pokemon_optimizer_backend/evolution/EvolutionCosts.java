package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.PowerUpCost;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.EvolutionStep;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evolution and second-move costs from Pokémon GO's game master, which PvPoke's gamemaster doesn't carry (#139).
 * {@code meta/evolution-costs.json} is made by tools/evolution-costs/generate.py.
 */
@Component
public class EvolutionCosts
{
    private static final String RESOURCE = "/meta/evolution-costs.json";

    /** "from>into" to the step */
    private final Map<String, EvolutionStep> steps = new HashMap<>();
    private final Map<String, PowerUpCost.Cost> secondMoves = new HashMap<>();

    public EvolutionCosts()
    {
        try (InputStream is = EvolutionCosts.class.getResourceAsStream(RESOURCE))
        {
            if (is == null)
            {
                throw new IllegalStateException(RESOURCE + " could not be found");
            }
            JsonNode all = new JsonMapper().readTree(is);
            for (Map.Entry<String, JsonNode> species : all.properties())
            {
                String from = species.getKey();
                JsonNode second = species.getValue().get("secondMove");
                if (second != null && !second.isNull())
                {
                    secondMoves.put(from, new PowerUpCost.Cost(second.get("stardust").asInt(), second.get("candy").asInt(), 0));
                }
                for (Map.Entry<String, JsonNode> evolution : species.getValue().get("evolutions").properties())
                {
                    JsonNode e = evolution.getValue();
                    List<String> conditions = new ArrayList<>();
                    e.get("conditions").forEach(c -> conditions.add(c.asString()));
                    steps.put(from + ">" + evolution.getKey(), new EvolutionStep(from, evolution.getKey(), e.get("candy").asInt(),
                        text(e.get("item")), e.get("itemCount").asInt(), text(e.get("lure")), List.copyOf(conditions)));
                }
            }
        }
        catch (IOException | JacksonException e)
        {
            throw new IllegalStateException(RESOURCE + " couldn't be loaded", e);
        }
    }

    private static String text(JsonNode node)
    {
        return node == null || node.isNull() ? null : node.asString();
    }

    /** What evolving {@code from} into {@code into} takes; empty when the game data doesn't have it. */
    public Optional<EvolutionStep> step(String from, String into)
    {
        return Optional.ofNullable(steps.get(from + ">" + into));
    }

    /** Stardust and Candy to unlock this species' second Charged Move, before any Shadow markup. */
    public Optional<PowerUpCost.Cost> secondMove(String speciesId)
    {
        return Optional.ofNullable(secondMoves.get(speciesId));
    }
}

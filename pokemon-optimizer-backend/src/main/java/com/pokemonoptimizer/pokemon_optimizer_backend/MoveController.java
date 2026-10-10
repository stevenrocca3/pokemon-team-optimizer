package com.pokemonoptimizer.pokemon_optimizer_backend;

import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController 
@RequestMapping("/api")

public class MoveController 
{
    private final MoveLookup moveLookup;
    public MoveController(MoveLookup ml)
    {
        moveLookup = ml;
    }

    @GetMapping("/moves")
    public List<Move> moves() {
        return moveLookup.findAll();
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.controller;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;


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

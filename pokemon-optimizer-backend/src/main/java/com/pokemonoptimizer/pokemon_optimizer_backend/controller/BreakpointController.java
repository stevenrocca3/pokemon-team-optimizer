package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.BreakpointRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.BreakpointReport;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.Breakpoints;

import jakarta.validation.Valid;

/** Public and stateless: the Species page sends the IVs being checked, logged in or not. */
@RestController
@RequestMapping("/api")
public class BreakpointController
{
    private final Breakpoints breakpoints;

    public BreakpointController(Breakpoints breakpoints)
    {
        this.breakpoints = breakpoints;
    }

    @PostMapping("/breakpoints")
    public BreakpointReport breakpoints(@Valid @RequestBody BreakpointRequest request)
    {
        try
        {
            return breakpoints.report(request.league(), request.pokemon());
        }
        catch (IllegalArgumentException e)
        {   // it can't play in this League; say why
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}

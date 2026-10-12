package com.pokemonoptimizer.pokemon_optimizer_backend.calculator;

public record IvResult(int atkIV, int defIV, int staIV, double level, 
    double effectiveAttack, double statProduct) {}

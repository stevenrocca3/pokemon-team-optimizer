package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController 
{
    record Me(String name, String email, String googleId) {}

    @GetMapping("/api/me")
    public Me me(@AuthenticationPrincipal OAuth2User user)
    {
        return new Me(
            user.getAttribute("name"), 
            user.getAttribute("email"), 
            user.getAttribute("sub"));
    }
}

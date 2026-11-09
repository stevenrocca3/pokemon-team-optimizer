package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pokemonoptimizer.pokemon_optimizer_backend.account.AccountService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class MeController
{
    /** Just what the header shows. The email and Google ID never leave the server. */
    record Me(String name) {}

    private final AccountService accountService;

    public MeController(AccountService accountService)
    {
        this.accountService = accountService;
    }

    @GetMapping("/api/me")
    public Me me(@AuthenticationPrincipal OidcUser user)
    {
        return new Me(user.getFullName());
    }

    /** Deletes the Trainer and their Collection, then logs them out, since the account behind the session is gone. */
    @DeleteMapping("/api/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal OidcUser user, Authentication authentication,
                         HttpServletRequest request, HttpServletResponse response)
    {
        accountService.deleteAccount(user.getSubject());
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }
}

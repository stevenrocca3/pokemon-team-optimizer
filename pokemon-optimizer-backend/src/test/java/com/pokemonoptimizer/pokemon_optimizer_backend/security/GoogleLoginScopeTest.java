package com.pokemonoptimizer.pokemon_optimizer_backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The login is kept in the database (SessionPersistenceTest), so Google is only asked for what the app uses: the ID
 * that ties a login to its Collection (openid) and the name in the menu (profile). Never the email address.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class GoogleLoginScopeTest
{
    @Autowired
    private MockMvcTester mvc;

    @Test
    public void loginAsksGoogleForTheIdAndNameButNotTheEmail()
    {
        String toGoogle = mvc.get().uri("/oauth2/authorization/google").exchange().getResponse().getHeader("Location");

        String scope = UriComponentsBuilder.fromUriString(toGoogle).build(false).getQueryParams().getFirst("scope");
        assertThat(scope).isEqualTo("openid%20profile");
    }
}

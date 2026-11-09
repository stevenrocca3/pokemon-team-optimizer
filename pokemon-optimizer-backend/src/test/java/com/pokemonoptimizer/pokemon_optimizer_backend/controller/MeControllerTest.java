package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.account.AccountService;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
public class MeControllerTest
{
    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    public void meIsJustTheNameToShowNotTheEmailOrGoogleId()
    {
        assertThat(mvc.get().uri("/api/me").with(oidcLogin().idToken(t -> t.subject("google-ash").claim("name", "Ash").claim("email", "ash@example.com"))))
            .hasStatusOk()
            .bodyJson().isStrictlyEqualTo("{\"name\":\"Ash\"}");
    }

    @Test
    public void deletingYourAccountRemovesItAndEndsTheSession()
    {
        MockHttpSession session = new MockHttpSession();

        assertThat(mvc.delete().uri("/api/me").session(session).with(csrf())
                .with(oidcLogin().idToken(t -> t.subject("google-ash"))))
            .hasStatus(HttpStatus.NO_CONTENT);

        verify(accountService).deleteAccount("google-ash");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    public void guestsCantDeleteAnAccount()
    {
        assertThat(mvc.delete().uri("/api/me").with(csrf())).hasStatus(HttpStatus.UNAUTHORIZED);
        verify(accountService, never()).deleteAccount(any());
    }

    @Test
    public void aForgedDeleteWithoutTheCsrfTokenIsRefused()
    {
        assertThat(mvc.delete().uri("/api/me").with(oidcLogin())).hasStatus(HttpStatus.FORBIDDEN);
        verify(accountService, never()).deleteAccount(any());
    }

    @Test
    public void loggingOutAnswers204InsteadOfRedirectingToALoginPage()
    {
        MockHttpSession session = new MockHttpSession();

        assertThat(mvc.post().uri("/api/logout").session(session).with(csrf()).with(oidcLogin()))
            .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(session.isInvalid()).isTrue();
    }
}

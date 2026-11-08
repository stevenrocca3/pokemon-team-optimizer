package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.FeedbackRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

@WebMvcTest(FeedbackController.class)
@Import(SecurityConfig.class)
public class FeedbackControllerTest
{
    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private FeedbackRepository feedbackRepository;

    @Test
    public void guestsCanSendFeedback()
    {   // no login: most visitors never make an account
        assertThat(mvc.post().uri("/api/feedback").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Team 1 lead feels wrong\",\"page\":\"/team-builder\"}"))
            .hasStatus(HttpStatus.CREATED);
        verify(feedbackRepository).save(argThat(saved ->
            saved.getMessage().equals("Team 1 lead feels wrong") && saved.getPage().equals("/team-builder") && saved.getCreatedAt() != null));
    }

    @Test
    public void anEmptyMessageIs400()
    {
        assertThat(mvc.post().uri("/api/feedback").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"   \",\"page\":\"/\"}"))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors[*].field").asArray().contains("message");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    public void aMessageOver2000CharactersIs400()
    {
        assertThat(mvc.post().uri("/api/feedback").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + "a".repeat(2001) + "\",\"page\":\"/\"}"))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors[*].field").asArray().contains("message");
    }

    @Test
    public void feedbackNeedsTheCsrfToken()
    {   // it writes to the database, unlike the calculation endpoints that skip CSRF
        assertThat(mvc.post().uri("/api/feedback").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hi\",\"page\":\"/\"}"))
            .hasStatus(HttpStatus.FORBIDDEN);
    }
}

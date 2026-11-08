package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.StarCount;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.RatingRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

@WebMvcTest(RatingController.class)
@Import(SecurityConfig.class)
public class RatingControllerTest
{
    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RatingRepository ratingRepository;

    @Test
    public void guestsCanRate()
    {   // no login: most visitors never make an account
        assertThat(mvc.post().uri("/api/ratings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stars\":4,\"page\":\"/team-builder\"}"))
            .hasStatus(HttpStatus.CREATED);
        verify(ratingRepository).save(argThat(saved ->
            saved.getStars() == 4 && saved.getPage().equals("/team-builder") && saved.getCreatedAt() != null));
    }

    @Test
    public void sixStarsIs400()
    {
        assertThat(mvc.post().uri("/api/ratings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stars\":6,\"page\":\"/\"}"))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors[*].field").asArray().contains("stars");
        verify(ratingRepository, never()).save(any());
    }

    @Test
    public void zeroStarsIs400()
    {
        assertThat(mvc.post().uri("/api/ratings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stars\":0,\"page\":\"/\"}"))
            .hasStatus(HttpStatus.BAD_REQUEST);
        verify(ratingRepository, never()).save(any());
    }

    @Test
    public void missingStarsIs400()
    {   // not quietly saved as 0 stars
        assertThat(mvc.post().uri("/api/ratings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"page\":\"/\"}"))
            .hasStatus(HttpStatus.BAD_REQUEST);
        verify(ratingRepository, never()).save(any());
    }

    @Test
    public void ratingNeedsTheCsrfToken()
    {   // it writes to the database, like feedback
        assertThat(mvc.post().uri("/api/ratings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"stars\":5,\"page\":\"/\"}"))
            .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    public void summaryShowsThePercentages()
    {
        when(ratingRepository.countByStars()).thenReturn(List.of(new StarCount(5, 3L), new StarCount(4, 1L)));

        assertThat(mvc.get().uri("/api/ratings/summary"))
            .hasStatus(HttpStatus.OK)
            .bodyJson().isLenientlyEqualTo("""
                {"total":4,"average":4.75,"percentByStars":{"5":75.0,"4":25.0,"3":0.0,"2":0.0,"1":0.0}}""");
    }
}

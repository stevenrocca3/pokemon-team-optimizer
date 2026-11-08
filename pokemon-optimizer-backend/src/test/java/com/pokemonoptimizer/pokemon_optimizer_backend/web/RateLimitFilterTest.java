package com.pokemonoptimizer.pokemon_optimizer_backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.pokemonoptimizer.pokemon_optimizer_backend.controller.FeedbackController;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.FeedbackRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

/**
 * The limiter in front of the API, seen from outside. Feedback has the smallest limit (5), so it's the easiest to hit.
 * The limiter lives as long as the test context, so each test uses its own client IP.
 */
@WebMvcTest(FeedbackController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"app.max-json-body=1KB", "app.client-ip-header=Fly-Client-IP"})
public class RateLimitFilterTest
{
    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private FeedbackRepository feedbackRepository;

    private MvcTestResult feedbackFrom(String ip, String message)
    {
        return mvc.post().uri("/api/feedback").with(csrf()).header("Fly-Client-IP", ip)
            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"" + message + "\",\"page\":\"/\"}").exchange();
    }

    @Test
    public void theSixthFeedbackInARowIs429WithRetryAfter()
    {
        for (int i = 0; i < 5; i++)
        {
            assertThat(feedbackFrom("10.0.0.1", "hi " + i)).hasStatus(HttpStatus.CREATED);
        }

        MvcTestResult sixth = feedbackFrom("10.0.0.1", "hi again");
        assertThat(sixth).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .headers().hasValue("Retry-After", "300");
        assertThat(sixth).bodyJson().extractingPath("$.detail").asString().contains("Too many requests");
    }

    @Test
    public void eachClientIpHasItsOwnLimit()
    {   // the host's edge sets Fly-Client-IP, which a visitor can't fake like X-Forwarded-For
        for (int i = 0; i < 5; i++) feedbackFrom("10.0.0.2", "hi " + i);

        assertThat(feedbackFrom("10.0.0.3", "hello")).hasStatus(HttpStatus.CREATED);
    }

    @Test
    public void aBodyOverTheSizeCapIs413()
    {   // 1 KB in this test; 4 MB in the app, double what a 10,000-Pokémon Team Builder request needs
        assertThat(feedbackFrom("10.0.0.4", "a".repeat(2000))).hasStatus(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    public void apiResponsesAskSearchEnginesNotToIndexThem()
    {
        assertThat(feedbackFrom("10.0.0.5", "hi")).headers().hasValue("X-Robots-Tag", "noindex");
    }
}

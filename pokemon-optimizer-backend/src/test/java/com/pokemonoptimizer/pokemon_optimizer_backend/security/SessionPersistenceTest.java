package com.pokemonoptimizer.pokemon_optimizer_backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Logins used to live in the app's memory, so every deploy (a new container) logged everyone out, and the cookie
 * died with the browser. Sessions now live in Postgres and the cookie lasts 30 days.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class SessionPersistenceTest
{
    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** Starting Google login is the first thing that needs a session: it remembers the OAuth state. */
    private MvcTestResult startLogin()
    {
        return mvc.get().uri("/oauth2/authorization/google").exchange();
    }

    @Test
    public void aSessionIsStoredInTheDatabase()
    {
        Integer before = jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class);

        startLogin();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class)).isEqualTo(before + 1);
    }

    @Test
    public void aSessionLastsThirtyDaysWithoutAVisit()
    {   // not Spring's default 30 minutes
        startLogin();

        Integer seconds = jdbc.queryForObject(
            "SELECT max_inactive_interval FROM spring_session ORDER BY creation_time DESC LIMIT 1", Integer.class);
        assertThat(seconds).isEqualTo(30 * 24 * 60 * 60);
    }

    @Test
    public void theSessionCookieOutlivesTheBrowserForThirtyDays()
    {
        // logout, account deletion and the privacy page all name this cookie
        String cookie = startLogin().getResponse().getHeaders("Set-Cookie").stream()
            .filter(header -> header.startsWith("JSESSIONID="))
            .findFirst().orElse(null);

        assertThat(cookie).isNotNull().contains("Max-Age=2592000").contains("HttpOnly").contains("SameSite=Lax");
    }
}

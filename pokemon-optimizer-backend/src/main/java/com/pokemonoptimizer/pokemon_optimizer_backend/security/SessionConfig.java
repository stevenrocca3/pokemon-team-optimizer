package com.pokemonoptimizer.pokemon_optimizer_backend.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Logins: sessions live in Postgres (Spring Session JDBC, V11). Spring Session ignores Boot's server.servlet.session.*
 * settings for its timeout and its cookie, so both are set here from server.servlet.session.timeout.
 */
@Configuration
public class SessionConfig
{
    /** Logout and account deletion clear this name, and the privacy page lists it. */
    static final String COOKIE_NAME = "JSESSIONID";

    @Bean
    SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sessionTimeout(
        @Value("${server.servlet.session.timeout}") Duration timeout)
    {
        return repository -> repository.setDefaultMaxInactiveInterval(timeout);
    }

    @Bean
    CookieSerializer cookieSerializer(
        @Value("${server.servlet.session.timeout}") Duration timeout,
        // true in production (HTTPS only); plain http://localhost in development
        @Value("${server.servlet.session.cookie.secure:false}") boolean secure)
    {
        DefaultCookieSerializer cookie = new DefaultCookieSerializer();
        cookie.setCookieName(COOKIE_NAME);
        cookie.setCookieMaxAge((int) timeout.toSeconds());   // outlives the browser; renewed on every visit
        cookie.setUseHttpOnlyCookie(true);
        cookie.setSameSite("Lax");   // Lax, not Strict: Google's redirect back after login has to carry it
        cookie.setUseSecureCookie(secure);
        return cookie;
    }
}

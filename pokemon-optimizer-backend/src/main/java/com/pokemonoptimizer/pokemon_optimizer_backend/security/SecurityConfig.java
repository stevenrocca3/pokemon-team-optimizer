package com.pokemonoptimizer.pokemon_optimizer_backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;

@Configuration
public class SecurityConfig 
{
    @Bean 
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
        // where Google login lands: the Vite dev server in development, the app itself ("/") in production
        @Value("${app.after-login-url}") String afterLoginUrl) throws Exception
    {
        http
            .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/collection/**", "/api/checklist", "/api/me").authenticated()
            .anyRequest().permitAll())
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .oauth2Login(oauth -> oauth.defaultSuccessUrl(afterLoginUrl, true))
            // the React app logs out with fetch, so answer 204 instead of redirecting to a login page that doesn't exist
            .logout(logout -> logout.logoutUrl("/api/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
            // These are pure calculations (POST only to carry a body or a file): nothing for a forged request to
            // change, and a fresh visitor's first call can go out before the XSRF-TOKEN cookie exists
            .csrf(csrf -> csrf.spa().ignoringRequestMatchers("/api/rank", "/api/team-builder", "/api/compare", "/api/breakpoints", "/api/import/**"))
            .addFilterAfter((request, response, chain) -> {
                ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();
                chain.doFilter(request, response);
            }, CsrfFilter.class);
        return http.build();
    }
}

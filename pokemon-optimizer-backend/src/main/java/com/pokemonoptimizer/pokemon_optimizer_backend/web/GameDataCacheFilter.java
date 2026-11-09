package com.pokemonoptimizer.pokemon_optimizer_backend.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Species, move and meta lists come from the data bundled with the build, so they only change on a deploy.
 * Browsers keep them for an hour, then revalidate with the ETag: an unchanged list costs a 304 with no body
 * instead of hundreds of KB. Runs before Spring Security, whose default "no-store" only applies when no
 * Cache-Control is set yet.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class GameDataCacheFilter extends ShallowEtagHeaderFilter
{
    static final String CACHE_CONTROL = "public, max-age=3600";

    public GameDataCacheFilter()
    {
        // weak (W/"...") because Tomcat refuses to gzip a response with a strong ETag (noCompressionStrongETag),
        // and the 300 KB lists matter far more compressed; weak tags still get 304s
        setWriteWeakETag(true);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        String path = request.getRequestURI();
        boolean gameData = path.equals("/api/moves") || path.equals("/api/species") || path.startsWith("/api/species/")
            || path.startsWith("/api/meta/");
        return !gameData || !("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL);
        super.doFilterInternal(request, response, chain);
    }
}

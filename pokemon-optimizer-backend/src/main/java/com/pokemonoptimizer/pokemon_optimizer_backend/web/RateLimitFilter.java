package com.pokemonoptimizer.pokemon_optimizer_backend.web;

import java.io.IOException;
import java.time.Clock;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import com.pokemonoptimizer.pokemon_optimizer_backend.web.RateLimiter.Decision;
import com.pokemonoptimizer.pokemon_optimizer_backend.web.RateLimiter.Limit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * In front of every /api call: per-client rate limits (ApiLimits), a cap on JSON body size, and a noindex header.
 * Runs before Spring Security so a flood is turned away before any login or CSRF work. The forwarded-headers
 * filter runs earlier still, so getRemoteAddr() is the visitor's address behind the host's proxy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter
{
    private final RateLimiter limiter = new RateLimiter(Clock.systemUTC(), 100_000);
    private final String clientIpHeader;
    private final long maxJsonBody;

    public RateLimitFilter(
        // a header the host's edge sets and visitors can't fake (e.g. Fly-Client-IP, CF-Connecting-IP); empty uses the remote address
        @Value("${app.client-ip-header:}") String clientIpHeader,
        // a 10,000-Pokémon Team Builder request is ~1.7 MB; file uploads have their own multipart limit
        @Value("${app.max-json-body:4MB}") DataSize maxJsonBody)
    {
        this.clientIpHeader = clientIpHeader;
        this.maxJsonBody = maxJsonBody.toBytes();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException
    {
        response.setHeader("X-Robots-Tag", "noindex");   // JSON isn't a page; robots.txt still lets crawlers fetch it to render pages

        boolean multipart = request.getContentType() != null && request.getContentType().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
        if (!multipart && request.getContentLengthLong() > maxJsonBody)
        {
            problem(response, HttpStatus.PAYLOAD_TOO_LARGE, "Request body is too large.");
            return;
        }

        Optional<Limit> limit = ApiLimits.forRequest(request.getMethod(), request.getRequestURI());
        if (limit.isPresent())
        {
            Decision decision = limiter.tryAcquire(limit.get(), clientIp(request));
            if (!decision.allowed())
            {
                response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
                problem(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests. Try again in " + decision.retryAfterSeconds() + " seconds.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request)
    {
        if (!clientIpHeader.isEmpty())
        {
            String fromEdge = request.getHeader(clientIpHeader);
            if (fromEdge != null && !fromEdge.isBlank())
            {
                return fromEdge.strip();
            }
        }
        return request.getRemoteAddr();
    }

    /** The same ProblemDetail JSON shape the controllers return, written by hand because this runs before Spring MVC. */
    private static void problem(HttpServletResponse response, HttpStatus status, String detail) throws IOException
    {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
            + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\"}");
    }
}

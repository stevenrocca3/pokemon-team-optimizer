package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Results from the battle analysis service over the private network (`app.analysis-url`: the analysis-api
 * container in production, localhost:8090 in development). Size-capped and timed out, and only job ids go into the URL.
 */
@Component
public class HttpAnalysisResults implements AnalysisResults
{
    /** A result (turn log + report) is ~150 KB for a 3-minute battle; anything near this is not one. */
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final JsonMapper JSON = new JsonMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String base;
    private final long maxBytes;

    @Autowired
    public HttpAnalysisResults(@Value("${app.analysis-url}") String baseUrl)
    {
        this(baseUrl, MAX_BYTES);
    }

    HttpAnalysisResults(String baseUrl, long maxBytes)
    {
        this.base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.maxBytes = maxBytes;
    }

    @Override
    public JsonNode result(String jobId)
    {
        if (!BattleHistoryService.isJobId(jobId))
        {
            throw new AnalysisNotFoundException();
        }
        URI uri = URI.create(base + "/api/v1/analyze/" + jobId + "/result");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("Accept", "application/json").GET().build();
        try
        {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body())
            {
                switch (response.statusCode())
                {
                    case 200 -> { }
                    case 404 -> throw new AnalysisNotFoundException();
                    case 409 -> throw new AnalysisNotFinishedException();
                    default -> throw new IllegalStateException(uri + " answered " + response.statusCode());
                }
                byte[] bytes = body.readNBytes((int) maxBytes + 1);
                if (bytes.length > maxBytes)
                {
                    throw new IllegalStateException(uri + " sent over " + maxBytes + " bytes");
                }
                return JSON.readTree(bytes);
            }
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Couldn't reach the battle analysis service", e);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted reading " + uri, e);
        }
    }
}

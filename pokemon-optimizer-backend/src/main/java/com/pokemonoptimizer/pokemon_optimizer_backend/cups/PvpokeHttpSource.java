package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PvPoke's open data over HTTPS (its GitHub repository's raw files, MIT-licensed). Every response is size-capped
 * and timed out, and meta group names, which come from the downloaded data, are checked before they go into a URL.
 */
@Component
public class PvpokeHttpSource implements PvpokeSource
{
    /** The built gamemaster is ~1.7 MB; anything near this is not what we asked for. */
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Pattern GROUP_NAME = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final JsonMapper JSON = new JsonMapper();

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final URI base;
    private final long maxBytes;

    @Autowired
    public PvpokeHttpSource(@Value("${app.cups.pvpoke-data-url}") String baseUrl)
    {
        this(baseUrl, MAX_BYTES);
    }

    PvpokeHttpSource(String baseUrl, long maxBytes)
    {
        this.base = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.maxBytes = maxBytes;
    }

    @Override
    public JsonNode gamemaster()
    {
        return get("gamemaster.json");
    }

    @Override
    public JsonNode metaGroup(String name)
    {
        if (name == null || !GROUP_NAME.matcher(name).matches())
        {
            throw new CupDataException("Unexpected meta group name '" + name + "'");
        }
        return get("groups/" + name + ".json");
    }

    private JsonNode get(String path)
    {
        URI uri = base.resolve(path);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("Accept", "application/json").GET().build();
        try
        {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body())
            {
                if (response.statusCode() != 200)
                {
                    throw new CupDataException(uri + " answered " + response.statusCode());
                }
                byte[] bytes = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
                if (bytes.length > maxBytes)
                {
                    throw new CupDataException(uri + " is over " + maxBytes + " bytes");
                }
                return JSON.readTree(bytes);
            }
        }
        catch (IOException | JacksonException e)
        {
            throw new CupDataException("Couldn't read " + uri + ": " + e.getMessage(), e);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new CupDataException("Interrupted reading " + uri, e);
        }
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** Issue #120: the result of a finished job, fetched from the battle analysis service (never from the browser). */
public class HttpAnalysisResultsTest
{
    private static final String DONE = "a".repeat(32);
    private static final String RUNNING = "b".repeat(32);
    private static final String HUGE = "c".repeat(32);

    private HttpServer server;
    private final List<String> requested = new CopyOnWriteArrayList<>();
    private String base;

    @BeforeEach
    public void start() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requested.add(path);
            int status = 200;
            String body;
            if (path.equals("/api/v1/analyze/" + DONE + "/result"))
            {
                body = "{\"turn_log\": {\"user_team\": [\"Swampert\"]}, \"report\": {\"score\": 61}}";
            }
            else if (path.equals("/api/v1/analyze/" + RUNNING + "/result"))
            {
                status = 409;
                body = "{\"detail\": \"This analysis isn’t finished yet.\", \"status\": \"processing_cv\"}";
            }
            else if (path.equals("/api/v1/analyze/" + HUGE + "/result"))
            {
                body = "{\"report\": {\"pad\": \"" + "x".repeat(5000) + "\"}}";
            }
            else
            {
                status = 404;
                body = "{\"detail\": \"No analysis with that ID.\"}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    public void stop()
    {
        server.stop(0);
    }

    // A finished job: the result comes back as JSON, read from the job's /result address.
    @Test
    public void readsAFinishedJobsResult()
    {
        HttpAnalysisResults results = new HttpAnalysisResults(base, 1_000_000);

        assertThat(results.result(DONE).path("report").path("score").asInt()).isEqualTo(61);
        assertThat(requested).containsExactly("/api/v1/analyze/" + DONE + "/result");
    }

    // A job still running (the service answers 409) is "not finished", so the browser can report it again later.
    @Test
    public void aRunningJobIsNotFinished()
    {
        assertThatThrownBy(() -> new HttpAnalysisResults(base, 1_000_000).result(RUNNING))
            .isInstanceOf(AnalysisNotFinishedException.class);
    }

    // A job the service doesn't know (404: never existed, or expired) is "not found".
    @Test
    public void anUnknownJobIsNotFound()
    {
        assertThatThrownBy(() -> new HttpAnalysisResults(base, 1_000_000).result("d".repeat(32)))
            .isInstanceOf(AnalysisNotFoundException.class);
    }

    // A response over the size cap isn't read into memory; it fails instead of being saved.
    @Test
    public void anOversizedResultIsRefused()
    {
        assertThatThrownBy(() -> new HttpAnalysisResults(base, 1_000).result(HUGE))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("bytes");
    }

    // Only job ids go into the URL: anything else is refused before a request is made.
    @Test
    public void onlyJobIdsReachTheUrl()
    {
        assertThatThrownBy(() -> new HttpAnalysisResults(base, 1_000_000).result("../../admin"))
            .isInstanceOf(AnalysisNotFoundException.class);
        assertThat(requested).isEmpty();
    }
}

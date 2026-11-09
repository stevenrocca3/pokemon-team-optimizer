package com.pokemonoptimizer.pokemon_optimizer_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Compression happens in the real Tomcat connector, so this needs a real server, not MockMvc. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompressionTest
{
    @LocalServerPort
    private int port;

    @Test
    void jsonResponsesAreGzippedWhenTheClientAcceptsIt() throws Exception
    {   // /api/moves is a few hundred KB of JSON; without server.compression it comes back raw
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/moves"))
                .header("Accept-Encoding", "gzip").build(),
            HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).hasValue("gzip");
    }

    @Test
    void cachedGameDataIsStillGzippedAndRevalidatesTo304() throws Exception
    {   // the ETag must not cost compression: Tomcat skips gzip when an ETag is strong
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<byte[]> first = client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/moves"))
                .header("Accept-Encoding", "gzip").build(),
            HttpResponse.BodyHandlers.ofByteArray());
        String etag = first.headers().firstValue("ETag").orElseThrow();

        HttpResponse<byte[]> again = client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/moves"))
                .header("Accept-Encoding", "gzip").header("If-None-Match", etag).build(),
            HttpResponse.BodyHandlers.ofByteArray());

        assertThat(first.headers().firstValue("Content-Encoding")).hasValue("gzip");
        assertThat(again.statusCode()).isEqualTo(304);
    }
}

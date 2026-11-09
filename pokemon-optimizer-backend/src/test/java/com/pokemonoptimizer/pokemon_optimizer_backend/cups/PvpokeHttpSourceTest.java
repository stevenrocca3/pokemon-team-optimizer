package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

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

public class PvpokeHttpSourceTest
{
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
            byte[] body;
            int status = 200;
            switch (path)
            {
                case "/data/gamemaster.json" -> body = "{\"timestamp\": \"2026-11-03\"}".getBytes(StandardCharsets.UTF_8);
                case "/data/groups/fantasy.json" -> body = "[{\"speciesId\": \"azumarill\"}]".getBytes(StandardCharsets.UTF_8);
                case "/data/groups/huge.json" -> body = ("[" + "1,".repeat(1000) + "1]").getBytes(StandardCharsets.UTF_8);
                default -> { status = 404; body = "Not Found".getBytes(StandardCharsets.UTF_8); }
            }
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/data/";
    }

    @AfterEach
    public void stop()
    {
        server.stop(0);
    }

    @Test
    public void readsTheGamemasterAndMetaGroups()
    {
        PvpokeHttpSource source = new PvpokeHttpSource(base, 1_000_000);

        assertThat(source.gamemaster().get("timestamp").asString()).isEqualTo("2026-11-03");
        assertThat(source.metaGroup("fantasy").get(0).get("speciesId").asString()).isEqualTo("azumarill");
    }

    @Test
    public void aMissingFileIsACupDataError()
    {
        assertThatThrownBy(() -> new PvpokeHttpSource(base, 1_000_000).metaGroup("ended"))
            .isInstanceOf(CupDataException.class).hasMessageContaining("404");
    }

    @Test
    public void aFileOverTheSizeLimitIsRefused()
    {
        assertThatThrownBy(() -> new PvpokeHttpSource(base, 500).metaGroup("huge"))
            .isInstanceOf(CupDataException.class).hasMessageContaining("bytes");
    }

    @Test
    public void groupNamesFromTheDataCantReachOtherPaths()
    {   // the name comes from PvPoke's gamemaster, so it's checked before it goes into a URL
        PvpokeHttpSource source = new PvpokeHttpSource(base, 1_000_000);

        assertThatThrownBy(() -> source.metaGroup("../../secrets")).isInstanceOf(CupDataException.class);
        assertThatThrownBy(() -> source.metaGroup("great?x=1")).isInstanceOf(CupDataException.class);
        assertThat(requested).isEmpty();
    }

    @Test
    public void nothingListeningIsACupDataError()
    {
        assertThatThrownBy(() -> new PvpokeHttpSource("http://127.0.0.1:1/data/", 1_000_000).gamemaster())
            .isInstanceOf(CupDataException.class);
    }
}

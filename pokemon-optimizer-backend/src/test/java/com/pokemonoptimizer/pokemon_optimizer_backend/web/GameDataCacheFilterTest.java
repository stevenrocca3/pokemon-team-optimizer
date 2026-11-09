package com.pokemonoptimizer.pokemon_optimizer_backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.pokemonoptimizer.pokemon_optimizer_backend.controller.CpController;
import com.pokemonoptimizer.pokemon_optimizer_backend.controller.MoveController;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

/** Species and move data only change when a new version is deployed, so browsers may keep it and revalidate cheaply. */
@WebMvcTest({MoveController.class, CpController.class})
@Import({SecurityConfig.class, GameDataCacheFilter.class})
public class GameDataCacheFilterTest
{
    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MoveLookup moveLookup;

    @MockitoBean
    private SpeciesLookup speciesLookup;

    @BeforeEach
    public void moves()
    {
        when(moveLookup.findAll()).thenReturn(List.of());
    }

    @Test
    public void gameDataMayBeCachedForAnHourAndCarriesAnETag()
    {
        MvcTestResult result = mvc.get().uri("/api/moves").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("max-age=3600").contains("public");
        assertThat(result.getResponse().getHeader("ETag")).isNotBlank();
    }

    @Test
    public void unchangedGameDataAnswers304WithNoBody()
    {
        String etag = mvc.get().uri("/api/moves").exchange().getResponse().getHeader("ETag");

        assertThat(mvc.get().uri("/api/moves").header("If-None-Match", etag))
            .hasStatus(HttpStatus.NOT_MODIFIED)
            .body().isEmpty();
    }

    @Test
    public void calculationsAreNotCached()
    {   // /api/rank answers a different question every time
        MvcTestResult result = mvc.post().uri("/api/rank").contentType(MediaType.APPLICATION_JSON)
            .content("{\"speciesId\":\"medicham\",\"league\":\"GREAT\"}").exchange();

        assertThat(result.getResponse().getHeader("ETag")).isNull();
        assertThat(result.getResponse().getHeader("Cache-Control")).doesNotContain("max-age=3600");
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.account.AccountService;
import com.pokemonoptimizer.pokemon_optimizer_backend.controller.MeController;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

/**
 * In production Spring serves the built React app (src/test/resources/static stands in for it here).
 * React Router owns the page URLs, so a reload on /team-builder must get the app, not a 404.
 */
@WebMvcTest(MeController.class)   // any small controller: the fallback is what is under test
@Import({SecurityConfig.class, SinglePageApp.class})
@MockitoBean(types = AccountService.class)
public class SinglePageAppTest
{
    @Autowired
    private MockMvcTester mvc;

    @Test
    public void aPageUrlGetsTheApp()
    {
        assertThat(mvc.get().uri("/team-builder")).hasStatusOk().bodyText().contains("DexPresso test shell");
    }

    @Test
    public void aNestedPageUrlGetsTheApp()
    {
        assertThat(mvc.get().uri("/species/medicham")).hasStatusOk().bodyText().contains("DexPresso test shell");
    }

    @Test
    public void anUnknownPageGetsTheAppToo()
    {   // the app shows its own Not Found page
        assertThat(mvc.get().uri("/no-such-page")).hasStatusOk().bodyText().contains("DexPresso test shell");
    }

    @Test
    public void anUnknownApiUrlIsStillA404()
    {   // a mistyped API call must fail loudly, not come back as a web page
        assertThat(mvc.get().uri("/api/no-such-endpoint")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    public void aMissingFileIsStillA404()
    {   // an old hashed bundle after a deploy: HTML in its place would break the page in confusing ways
        assertThat(mvc.get().uri("/assets/index-old.js")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    public void hashedBuildFilesAreCachedForAYearAsImmutable()
    {   // Vite puts a content hash in every /assets file name, so a new build always means a new URL
        assertThat(mvc.get().uri("/assets/index-Ab12Cd34.js"))
            .hasStatusOk()
            .hasHeader("Cache-Control", "max-age=31536000, public, immutable");
    }

    @Test
    public void theAppShellIsAlwaysRevalidatedSoANewDeployShowsUpAtOnce()
    {
        assertThat(mvc.get().uri("/team-builder")).hasStatusOk()
            .headers().hasValue("Cache-Control", "no-cache");
    }

    @Test
    public void spritesAreCachedForAMonth()
    {   // the site's own Pokémon sprites: fixed names, so not immutable, but they hardly ever change
        assertThat(mvc.get().uri("/sprites/308.png"))
            .hasStatusOk()
            .hasHeader("Cache-Control", "max-age=2592000, public");
    }

    @Test
    public void aMissingSpriteIsA404()
    {   // PokemonArt shows its placeholder for a 404; an HTML page in its place would be a broken image
        assertThat(mvc.get().uri("/sprites/9999.png")).hasStatus(HttpStatus.NOT_FOUND);
    }
}


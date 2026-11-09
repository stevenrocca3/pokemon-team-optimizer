package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.MetaLookup;

@WebMvcTest(MetaController.class)
@Import({SecurityConfig.class, SpeciesLookup.class, MoveLookup.class, MetaLookup.class})
public class MetaControllerTest
{
    @Autowired private MockMvcTester mvc;

    @Test
    public void theChecklistForALeagueIsItsMetaWithShadowsSplitOut()
    {   // great.json: 44 entries, the second is annihilape_shadow
        assertThat(mvc.get().uri("/api/meta/GREAT"))
            .hasStatusOk()
            .bodyJson()
            .hasPathSatisfying("$.length()", n -> assertThat(n).asNumber().isEqualTo(44))
            .hasPathSatisfying("$[1].speciesId", id -> assertThat(id).asString().isEqualTo("annihilape"))
            .hasPathSatisfying("$[1].shadow", s -> assertThat(s).asBoolean().isTrue());
    }

    @Test
    public void guestsCanReadItAndBrowsersCacheIt()
    {   // the meta only changes on a deploy
        assertThat(mvc.get().uri("/api/meta/MASTER"))
            .hasStatusOk()
            .hasHeader("Cache-Control", "public, max-age=3600");
    }

    @Test
    public void anUnknownLeagueIs400()
    {
        assertThat(mvc.get().uri("/api/meta/LITTLE")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}

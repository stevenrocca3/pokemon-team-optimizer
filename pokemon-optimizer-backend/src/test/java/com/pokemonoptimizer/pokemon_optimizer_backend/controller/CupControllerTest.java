package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.cups.Cup;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.CupCatalog;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.CupRules;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.MetaLookup;

@WebMvcTest(CupController.class)
@Import({ SecurityConfig.class, SpeciesLookup.class })
public class CupControllerTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MetaLookup META = new MetaLookup(SPECIES, new MoveLookup());

    @Autowired private MockMvcTester mvc;
    @MockitoBean private CupCatalog cups;

    private static final Cup FANTASY = new Cup("fantasy-1500", "Fantasy Cup", League.GREAT, 1500, 3,
        List.of("Only Steel, Dragon and Fairy types."), new CupRules(List.of(), List.of()), META.meta(League.GREAT).subList(0, 5), "2026-11-03");

    @Test
    public void guestsCanListTheLiveCups()
    {
        when(cups.cups()).thenReturn(List.of(FANTASY));

        assertThat(mvc.get().uri("/api/cups"))
            .hasStatusOk()
            .hasHeader("Cache-Control", "public, max-age=3600")
            .bodyJson()
            .isLenientlyEqualTo("""
                [{"key": "fantasy-1500", "title": "Fantasy Cup", "league": "GREAT", "partySize": 3,
                  "rules": ["Only Steel, Dragon and Fairy types."], "metaSize": 5, "updated": "2026-11-03"}]""");
    }

    @Test
    public void aCupsMetaIsTheChecklistsListWithShadowsSplitOut()
    {   // the Great League meta's second Pokémon is annihilape_shadow
        when(cups.find("fantasy-1500")).thenReturn(Optional.of(FANTASY));

        assertThat(mvc.get().uri("/api/meta/cup/fantasy-1500"))
            .hasStatusOk()
            .bodyJson()
            .hasPathSatisfying("$.length()", n -> assertThat(n).asNumber().isEqualTo(5))
            .hasPathSatisfying("$[1].speciesId", id -> assertThat(id).asString().isEqualTo("annihilape"))
            .hasPathSatisfying("$[1].shadow", s -> assertThat(s).asBoolean().isTrue());
    }

    // #117: the checklist's Cups tab shows each Pokémon's types, like the League grids do
    @Test
    public void aCupsMetaComesWithEachPokemonsTypes()
    {   // the Great League meta's first five: Altaria, Shadow Annihilape, Araquanid, Azumarill, ...
        when(cups.find("fantasy-1500")).thenReturn(Optional.of(FANTASY));

        assertThat(mvc.get().uri("/api/meta/cup/fantasy-1500"))
            .hasStatusOk()
            .bodyJson()
            .hasPathSatisfying("$[0].types", t -> assertThat(t).asArray().containsExactly("dragon", "flying"))
            .hasPathSatisfying("$[1].types", t -> assertThat(t).asArray().containsExactly("fighting", "ghost"))
            .hasPathSatisfying("$[3].types", t -> assertThat(t).asArray().containsExactly("water", "fairy"));
    }

    @Test
    public void aCupThatsNotRunningIs404()
    {
        when(cups.find("halloween-1500")).thenReturn(Optional.empty());

        assertThat(mvc.get().uri("/api/meta/cup/halloween-1500")).hasStatus(HttpStatus.NOT_FOUND);
    }
}

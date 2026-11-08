package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.importer.PokeGenieCsvTest;
import com.pokemonoptimizer.pokemon_optimizer_backend.importer.PokeGenieMapper;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

@WebMvcTest(ImportController.class)
@Import({SecurityConfig.class, PokeGenieMapper.class, SpeciesLookup.class, MoveLookup.class})
public class ImportControllerTest
{
    @Autowired
    private MockMvcTester mvc;

    private static MockMultipartFile csv(String content)
    {
        return new MockMultipartFile("file", "export.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void guestsCanPreviewAPokeGenieExport()
    {   // no oidcLogin(): the preview is public, nothing is saved
        String content = PokeGenieCsvTest.HEADER
            + "1,Swampert,,260,,1500,145,0,15,14,64.4,20.0,20.0,Mud Shot,Hydro Cannon,Earthquake,"
            + "2026-11-08 10:00,2026-11-08 10:00,11/8/2026,80kg,1.5m,0,1,0,2500,,,,,,,,,,,,,,,,,,,,,,,,,\n";

        assertThat(mvc.post().uri("/api/import/poke-genie").multipart().file(csv(content)).with(csrf()))
            .hasStatusOk()
            .bodyJson().extractingPath("$.pokemon[0].speciesId").asString().isEqualTo("swampert");
    }

    @Test
    public void aFileThatIsntAPokeGenieExportIs400()
    {
        assertThat(mvc.post().uri("/api/import/poke-genie").multipart().file(csv("name,cp\nSwampert,1500\n")).with(csrf()))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.detail").asString().contains("Poké Genie");
    }
}

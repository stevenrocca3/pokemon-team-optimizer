package com.pokemonoptimizer.pokemon_optimizer_backend.controller;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(CpController.class)
@MockitoBean(types = SpeciesLookup.class)
@Import(SecurityConfig.class)
public class CpControllerTest
{
    @Autowired
    private MockMvcTester mvc;

    //./mvnw test -Dtest=SpeciesControllerTest 2>&1 | grep "Caused by"
    @Test
    public void unknownSpeciesReturns400WithDetail()
    {
        String body = """
                 {"speciesId":"notreal","hasCap":true,"cap":1500,"hasBestBuddy":false}
                """;
        assertThat(mvc.post().uri("/api/rank")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.detail").asString().contains("notreal");
    }

}


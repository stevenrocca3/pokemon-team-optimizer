package com.pokemonoptimizer.pokemon_optimizer_backend.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

@WebMvcTest(EvolutionController.class)
@Import(SecurityConfig.class)
public class EvolutionControllerTest
{
    @Autowired private MockMvcTester mvc;
    @MockitoBean private EvolutionProjector projector;   // the maths is EvolutionProjectorTest's job

    private static final String DRATINI = """
            {"speciesId":"dratini","atkIv":0,"defIv":14,"staIv":12,"level":15,"shadow":true}""";

    @Test
    public void guestsCanProjectWithoutLoggingIn()
    {   // a calculation like the IV rankings: no login, no CSRF token
        when(projector.find(eq("dratini"), eq(0), eq(14), eq(12), eq(15.0), eq(true)))
            .thenReturn(Optional.of(new EvolutionProjection("dratini", "Dratini", true, List.of())));

        assertThat(mvc.post().uri("/api/evolutions").contentType(MediaType.APPLICATION_JSON).content(DRATINI))
            .hasStatusOk()
            .bodyJson().extractingPath("$.name").asString().isEqualTo("Dratini");
    }

    @Test
    public void shadowAndLevelAreOptional()
    {
        when(projector.find(eq("dratini"), eq(0), eq(14), eq(12), eq(null), eq(false)))
            .thenReturn(Optional.of(new EvolutionProjection("dratini", "Dratini", false, List.of())));

        assertThat(mvc.post().uri("/api/evolutions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"speciesId\":\"dratini\",\"atkIv\":0,\"defIv\":14,\"staIv\":12}"))
            .hasStatusOk();
    }

    @Test
    public void anUnknownSpeciesIs404()
    {
        when(projector.find(any(), any(Integer.class), any(Integer.class), any(Integer.class), any(), any(Boolean.class)))
            .thenReturn(Optional.empty());

        assertThat(mvc.post().uri("/api/evolutions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"speciesId\":\"missingno\",\"atkIv\":0,\"defIv\":0,\"staIv\":0}"))
            .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    public void anIvOver15Is400()
    {
        assertThat(mvc.post().uri("/api/evolutions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"speciesId\":\"dratini\",\"atkIv\":16,\"defIv\":14,\"staIv\":12}"))
            .hasStatus(HttpStatus.BAD_REQUEST);
    }
}

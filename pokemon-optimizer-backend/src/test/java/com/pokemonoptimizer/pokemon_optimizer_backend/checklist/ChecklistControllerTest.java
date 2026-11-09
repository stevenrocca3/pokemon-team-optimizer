package com.pokemonoptimizer.pokemon_optimizer_backend.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

@WebMvcTest(ChecklistController.class)
@Import({SecurityConfig.class, SpeciesLookup.class})
public class ChecklistControllerTest
{
    @Autowired private MockMvcTester mvc;
    @MockitoBean private ChecklistService checklistService;

    private static final OwnedSpecies MEDICHAM = new OwnedSpecies("medicham", false);

    private MockMvcTester.MockMvcRequestBuilder put(String body)
    {
        return mvc.put().uri("/api/checklist").with(csrf()).with(oidcLogin().idToken(t -> t.subject("google-ash")))
            .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    public void aTrainerGetsTheirChecklist()
    {
        when(checklistService.checklist("google-ash")).thenReturn(List.of(MEDICHAM));

        assertThat(mvc.get().uri("/api/checklist").with(oidcLogin().idToken(t -> t.subject("google-ash"))))
            .hasStatusOk()
            .bodyJson().extractingPath("$[0].speciesId").isEqualTo("medicham");
    }

    @Test
    public void guestsKeepTheirChecklistInTheBrowser()
    {
        assertThat(mvc.get().uri("/api/checklist")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void savingReplacesTheChecklist()
    {
        List<OwnedSpecies> sent = List.of(MEDICHAM, new OwnedSpecies("registeel", true));
        when(checklistService.replace("google-ash", sent)).thenReturn(sent);

        assertThat(put("""
                {"species":[{"speciesId":"medicham"},{"speciesId":"registeel","shadow":true}]}
                """))
            .hasStatusOk()
            .bodyJson().extractingPath("$[1].shadow").isEqualTo(true);
    }

    @Test
    public void anUnknownSpeciesIs400AndNothingIsSaved()
    {
        assertThat(put("""
                {"species":[{"speciesId":"medicham"},{"speciesId":"missingno"}]}
                """))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.detail").asString().contains("missingno");
        verify(checklistService, never()).replace(anyString(), any());
    }

    @Test
    public void aShadowThatCantExistIs400()
    {   // Medicham has never been a Shadow
        assertThat(put("""
                {"species":[{"speciesId":"medicham","shadow":true}]}
                """))
            .hasStatus(HttpStatus.BAD_REQUEST);
        verify(checklistService, never()).replace(eq("google-ash"), any());
    }
}

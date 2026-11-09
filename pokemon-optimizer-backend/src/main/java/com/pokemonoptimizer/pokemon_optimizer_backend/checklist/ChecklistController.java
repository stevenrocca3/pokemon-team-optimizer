package com.pokemonoptimizer.pokemon_optimizer_backend.checklist;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

import jakarta.validation.Valid;

/**
 * The logged-in Trainer's Collection checklist: species they own, ticked without exact IVs. Guests keep theirs
 * in the browser and send it straight to the Team Builder.
 */
@RestController
@RequestMapping("/api")
public class ChecklistController
{
    private final ChecklistService checklistService;
    private final SpeciesLookup speciesLookup;

    public ChecklistController(ChecklistService checklistService, SpeciesLookup speciesLookup)
    {
        this.checklistService = checklistService;
        this.speciesLookup = speciesLookup;
    }

    @GetMapping("/checklist")
    public List<OwnedSpecies> checklist(@AuthenticationPrincipal OidcUser user)
    {
        return checklistService.checklist(user.getSubject());
    }

    /** All or nothing: one bad entry and the saved checklist stays as it was. */
    @PutMapping("/checklist")
    public List<OwnedSpecies> replace(@Valid @RequestBody ChecklistRequest request, @AuthenticationPrincipal OidcUser user)
    {
        for (OwnedSpecies owned : request.species())
        {
            Optional<SpeciesType> species = speciesLookup.findById(owned.speciesId());
            if (species.isEmpty())
            {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown species: " + owned.speciesId());
            }
            if (owned.shadow() && !species.get().shadowEligible())
            {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, species.get().speciesName() + " can't be a Shadow.");
            }
        }
        return checklistService.replace(user.getSubject(), request.species());
    }
}

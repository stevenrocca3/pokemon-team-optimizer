package com.pokemonoptimizer.pokemon_optimizer_backend.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChecklistService.class)
public class ChecklistServiceTest
{
    @Autowired private ChecklistService checklistService;
    @Autowired private TestEntityManager entityManager;

    private static final OwnedSpecies MEDICHAM = new OwnedSpecies("medicham", false);
    private static final OwnedSpecies REGISTEEL = new OwnedSpecies("registeel", false);
    private static final OwnedSpecies SHADOW_REGISTEEL = new OwnedSpecies("registeel", true);

    @Test
    public void replacingStoresTheChecklistInSpeciesOrder()
    {
        entityManager.persist(new Trainer("google-ash"));

        checklistService.replace("google-ash", List.of(REGISTEEL, MEDICHAM, SHADOW_REGISTEEL));
        entityManager.flush();
        entityManager.clear();

        assertThat(checklistService.checklist("google-ash")).containsExactly(MEDICHAM, REGISTEEL, SHADOW_REGISTEEL);
    }

    @Test
    public void replacingAgainSwapsTheWholeListEvenWhereItOverlaps()
    {   // Medicham is in both: the old row must go before the new one goes in, or the unique key trips
        entityManager.persist(new Trainer("google-ash"));
        checklistService.replace("google-ash", List.of(MEDICHAM, REGISTEEL));
        entityManager.flush();

        List<OwnedSpecies> saved = checklistService.replace("google-ash", List.of(MEDICHAM, SHADOW_REGISTEEL));
        entityManager.flush();
        entityManager.clear();

        assertThat(saved).containsExactly(MEDICHAM, SHADOW_REGISTEEL);
        assertThat(checklistService.checklist("google-ash")).containsExactly(MEDICHAM, SHADOW_REGISTEEL);
    }

    @Test
    public void aSpeciesTickedTwiceIsKeptOnce()
    {
        entityManager.persist(new Trainer("google-ash"));

        assertThat(checklistService.replace("google-ash", List.of(MEDICHAM, MEDICHAM))).containsExactly(MEDICHAM);
    }

    @Test
    public void otherTrainersKeepTheirChecklists()
    {
        entityManager.persist(new Trainer("google-ash"));
        entityManager.persist(new Trainer("google-misty"));
        checklistService.replace("google-misty", List.of(REGISTEEL));

        checklistService.replace("google-ash", List.of(MEDICHAM));
        entityManager.flush();
        entityManager.clear();

        assertThat(checklistService.checklist("google-misty")).containsExactly(REGISTEEL);
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemon;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemonRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AccountService.class)
public class AccountServiceTest
{
    @Autowired private AccountService accountService;
    @Autowired private TrainerRepository trainerRepository;
    @Autowired private CaughtPokemonRepository caughtPokemonRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    public void deletingAnAccountRemovesTheTrainerAndTheirWholeCollectionButNobodyElses()
    {
        Trainer ash = entityManager.persist(new Trainer("google-ash"));
        Trainer misty = entityManager.persist(new Trainer("google-misty"));
        entityManager.persist(medicham(ash));
        entityManager.persist(medicham(ash));
        CaughtPokemon mistys = entityManager.persist(medicham(misty));
        entityManager.flush();

        accountService.deleteAccount("google-ash");
        entityManager.flush();
        entityManager.clear();

        assertThat(trainerRepository.findByGoogleId("google-ash")).isEmpty();
        assertThat(caughtPokemonRepository.findByTrainer(misty)).extracting(CaughtPokemon::getId).containsExactly(mistys.getId());
        assertThat(trainerRepository.findByGoogleId("google-misty")).isPresent();
        assertThat(entityManager.getEntityManager()
            .createQuery("SELECT COUNT(c) FROM CaughtPokemon c WHERE c.trainer.googleId = 'google-ash'", Long.class)
            .getSingleResult()).isZero();
    }

    @Test
    public void deletingAnAccountThatIsAlreadyGoneDoesNothing()
    {   // a second click, or two tabs: still a success, not a 500
        accountService.deleteAccount("google-nobody");
    }

    private static CaughtPokemon medicham(Trainer trainer)
    {
        return new CaughtPokemon("medicham", 0, 15, 15, 22.5, false, "COUNTER", List.of("DYNAMIC_PUNCH", "ICE_PUNCH"), trainer);
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pokemonoptimizer.pokemon_optimizer_backend.checklist.ChecklistRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.history.BattleLogRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemonRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;

/** A Trainer's right to be forgotten: everything tied to their Google account goes, in one transaction. */
@Service
public class AccountService
{
    private final TrainerRepository trainerRepository;
    private final CaughtPokemonRepository caughtPokemonRepository;
    private final ChecklistRepository checklistRepository;
    private final BattleLogRepository battleLogRepository;

    public AccountService(TrainerRepository trainerRepository, CaughtPokemonRepository caughtPokemonRepository,
                          ChecklistRepository checklistRepository, BattleLogRepository battleLogRepository)
    {
        this.trainerRepository = trainerRepository;
        this.caughtPokemonRepository = caughtPokemonRepository;
        this.checklistRepository = checklistRepository;
        this.battleLogRepository = battleLogRepository;
    }

    /** Already gone (a second click, another tab) is fine: there's nothing left to delete. */
    @Transactional
    public void deleteAccount(String googleId)
    {
        trainerRepository.findByGoogleId(googleId).ifPresent(trainer -> {
            // the Collection, checklist and battle history first: they point at the Trainer
            battleLogRepository.deleteOwnedBy(trainer);
            caughtPokemonRepository.deleteByTrainer(trainer);
            checklistRepository.deleteByTrainer(trainer);
            trainerRepository.delete(trainer);
        });
    }
}

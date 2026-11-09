package com.pokemonoptimizer.pokemon_optimizer_backend.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.CaughtPokemonRepository;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;

/** A Trainer's right to be forgotten: everything tied to their Google account goes, in one transaction. */
@Service
public class AccountService
{
    private final TrainerRepository trainerRepository;
    private final CaughtPokemonRepository caughtPokemonRepository;

    public AccountService(TrainerRepository trainerRepository, CaughtPokemonRepository caughtPokemonRepository)
    {
        this.trainerRepository = trainerRepository;
        this.caughtPokemonRepository = caughtPokemonRepository;
    }

    /** Already gone (a second click, another tab) is fine: there's nothing left to delete. */
    @Transactional
    public void deleteAccount(String googleId)
    {
        trainerRepository.findByGoogleId(googleId).ifPresent(trainer -> {
            caughtPokemonRepository.deleteByTrainer(trainer);   // the Collection first: it points at the Trainer
            trainerRepository.delete(trainer);
        });
    }
}

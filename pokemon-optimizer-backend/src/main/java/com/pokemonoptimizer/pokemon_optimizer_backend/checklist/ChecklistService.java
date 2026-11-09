package com.pokemonoptimizer.pokemon_optimizer_backend.checklist;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;

/** A Trainer's Collection checklist. The page sends the whole list on each change, so saving is a replace. */
@Service
public class ChecklistService
{
    private static final Comparator<OwnedSpecies> SPECIES_ORDER =
        Comparator.comparing(OwnedSpecies::speciesId).thenComparing(OwnedSpecies::shadow);

    private final ChecklistRepository checklistRepository;
    private final TrainerRepository trainerRepository;

    public ChecklistService(ChecklistRepository checklistRepository, TrainerRepository trainerRepository)
    {
        this.checklistRepository = checklistRepository;
        this.trainerRepository = trainerRepository;
    }

    @Transactional(readOnly = true)
    public List<OwnedSpecies> checklist(String googleId)
    {
        return checklistRepository.findByTrainer(trainer(googleId)).stream()
            .map(ChecklistEntry::toOwnedSpecies)
            .sorted(SPECIES_ORDER)
            .toList();
    }

    /** Returns what was saved: the list in species order, each species once. */
    @Transactional
    public List<OwnedSpecies> replace(String googleId, List<OwnedSpecies> species)
    {
        Trainer trainer = trainer(googleId);
        List<OwnedSpecies> unique = new LinkedHashSet<>(species).stream().sorted(SPECIES_ORDER).toList();
        checklistRepository.deleteByTrainer(trainer);
        checklistRepository.saveAll(unique.stream().map(owned -> new ChecklistEntry(trainer, owned)).toList());
        return unique;
    }

    private Trainer trainer(String googleId)
    {
        return trainerRepository.findByGoogleId(googleId).orElseThrow();
    }
}

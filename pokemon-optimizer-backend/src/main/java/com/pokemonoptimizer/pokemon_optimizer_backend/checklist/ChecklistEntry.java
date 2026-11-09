package com.pokemonoptimizer.pokemon_optimizer_backend.checklist;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** One ticked species on a Trainer's checklist (V7). */
@Entity
@Table(name = "owned_species")
public class ChecklistEntry
{
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "owned_species_ids")
    @SequenceGenerator(name = "owned_species_ids", sequenceName = "owned_species_seq", allocationSize = 50)
    private Long id;
    @ManyToOne(optional = false)
    private Trainer trainer;
    @Column(nullable = false)
    private String speciesId;
    @Column(nullable = false)
    private boolean shadow;

    protected ChecklistEntry() {}

    public ChecklistEntry(Trainer trainer, OwnedSpecies owned)
    {
        this.trainer = trainer;
        this.speciesId = owned.speciesId();
        this.shadow = owned.shadow();
    }

    public OwnedSpecies toOwnedSpecies()
    {
        return new OwnedSpecies(speciesId, shadow);
    }
}

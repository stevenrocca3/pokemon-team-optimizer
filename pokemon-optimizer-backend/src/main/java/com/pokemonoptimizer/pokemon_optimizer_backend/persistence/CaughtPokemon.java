package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;



@Entity
public class CaughtPokemon 
{
    // ids come 50 at a time from a sequence (V6), so an import's INSERTs go out in JDBC batches; IDENTITY
    // made Hibernate send each row alone to learn its id, ~3 s for a 5,000-Pokémon import even on localhost
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "caught_pokemon_ids")
    @SequenceGenerator(name = "caught_pokemon_ids", sequenceName = "caught_pokemon_seq", allocationSize = 50)
    private Long id;
    private String speciesId; 
    private int atkIv, defIv, staIv;
    private Double level;   // null: added without a CP, so the level isn't known
    protected CaughtPokemon(){}
    private String fastMove;
    private String chargedMove1;
    private String chargedMove2;   // null when it only has one charged move
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean shadow;

    @ManyToOne(optional = false)
    private Trainer trainer;
   
    public CaughtPokemon(String speciesId, int atkIv, int defIv, int staIv, Double level, boolean shadow,
                     String fastMove, List<String> chargedMoves, Trainer trainer)
    {
       this.speciesId = speciesId;
       this.atkIv = atkIv;
       this.defIv = defIv;
       this.staIv = staIv;
       this.level = level;
       this.shadow = shadow;
       this.fastMove = fastMove;
       this.chargedMove1 = chargedMoves.get(0);
       this.chargedMove2 = chargedMoves.size() == 2 ? chargedMoves.get(1) : null;
       this.trainer = trainer;
    }

    public Long getId() { return id; }
    public String getSpeciesId() { return speciesId; }
    public int getAtkIv() { return atkIv; }
    public int getDefIv() { return defIv; }
    public int getStaIv() { return staIv; }
    public Double getLevel() { return level;}
    public boolean isShadow() { return shadow; }
    public String getFastMove() { return fastMove; }
    public List<String> getChargedMoves() 
    { 
        List<String> chargedMoves = new ArrayList<>();
        chargedMoves.add(chargedMove1);
        if (chargedMove2 != null)
        {
            chargedMoves.add(chargedMove2);
        }
        return chargedMoves;
     }

    /** Powered up since it was saved, or its level is now known; Pokémon never lose levels, so a lower one is ignored. */
    public void raiseLevelTo(double newLevel)
    {
        level = level == null ? newLevel : Math.max(level, newLevel);
    }
}

    


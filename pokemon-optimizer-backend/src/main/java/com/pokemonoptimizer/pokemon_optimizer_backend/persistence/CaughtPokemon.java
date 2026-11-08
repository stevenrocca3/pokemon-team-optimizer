package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;



@Entity
public class CaughtPokemon 
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String speciesId; 
    private int atkIv, defIv, staIv;
    private double level;
    protected CaughtPokemon(){}
    private String fastMove;
    private String chargedMove1;
    private String chargedMove2;   // null when it only has one charged move

    @ManyToOne(optional = false)
    private Trainer trainer;
   
    public CaughtPokemon(String speciesId, int atkIv, int defIv, int staIv, double level,
                     String fastMove, List<String> chargedMoves, Trainer trainer)
    {
       this.speciesId = speciesId;
       this.atkIv = atkIv;
       this.defIv = defIv;
       this.staIv = staIv;
       this.level = level;
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
    public double getLevel() { return level;}
    public String getFastMove() { return fastMove; }
    public List<String> getChargedMoves() 
    { 
        List<String> chargedMoves = new ArrayList<>();
        chargedMoves.add(chargedMove1);
        chargedMoves.add(chargedMove2);
        return chargedMoves;
     }
}

    


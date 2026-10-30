package com.pokemonoptimizer.pokemon_optimizer_backend.persistence;
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

    @ManyToOne(optional = false)
    private Trainer trainer;
   
    public CaughtPokemon(String speciesId, int atkIv, int defIv, int staIv, double level, Trainer trainer)
    {
       this.speciesId = speciesId;
       this.atkIv = atkIv;
       this.defIv = defIv;
       this.staIv = staIv;
       this.level = level;
       this.trainer = trainer;
    }

    public Long getId()
    {
        return id;
    }
    public String getSpeciesId()
    {
        return speciesId;
    }
    public int getAtkIv()
    {
        return atkIv;
    }
    public int getDefIv()
    {
        return defIv;
    }
    public int getStaIv()
    {
        return staIv;
    }
    public double getLevel()
    {
        return level;
    }

    
}

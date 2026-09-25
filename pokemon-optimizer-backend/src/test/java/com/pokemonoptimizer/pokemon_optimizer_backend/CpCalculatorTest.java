package com.pokemonoptimizer.pokemon_optimizer_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.pokemonoptimizer.CpCalculator;
import com.pokemonoptimizer.RankedIvResult;

@SpringBootTest 
public class CpCalculatorTest {

    // Sweep atk IV 0-15 holding def/sta at the rank 1 values, to see the level curve
    // for (int atk = 0; atk <= 15; atk++) {
    // double level = findMaxLevel(118, 111, 128, atk, atk, atk, true, 1500, false); //Holding Sta/Def constant iterate attack
    // double level1 = findMaxLevel(118, 111, 128, atk, atk, atk, true, 500, false);
    // System.out.println("ATK/DEF/STA IV " + atk + " (cap 1500) -> max level " + level);
    // System.out.println("ATK/DEF/STA IV " + atk + " (cap 500) -> max level " + level1);
    // }
    //Tests the cp boundary is handling query right, result prints 19.5 = 489, 20 = 501
    // System.out.println(calculateCP(118, 111, 128, 0, 0, 0, 19.5)); // should be <= 500
    // System.out.println(calculateCP(118, 111, 128, 0, 0, 0, 20.0)); // should be > 500
    @Test
    public void testCalculateCP_Bulbasaur_Level40() 
    {
        int cp = CpCalculator.calculateCP(118, 111, 128, 15, 15, 15, 40);
        assertEquals(1115, cp);

    }

    @Test
    public void testCalculateCP_Bulbasaur_Level50() 
    {
        int cp = CpCalculator.calculateCP(118, 111, 128, 15, 15, 15, 50);
        assertEquals(1260, cp);
    }
    //Test Case of Cradily Max Attack Stat + Max Stat Product, should be 104.8 after truncating
    //IvResult result = calculateStatProduct(152, 194, 200, 2, 13, 13, 26);
    //System.out.println(result.effectiveAttack()); //104.8993946 pass
    //System.out.println(result.statProduct()); //Within rounding error pass

    // RankedIvResult[] cradilyResults = rankAllCombos(152, 194, 200, false, 6673, false); //Master league test (6673 is highest possible CP in game)
    // RankedIvResult[] cradilyResults = rankAllCombos(152, 194, 200, true, 2500, false); //Ultra league test
    // RankedIvResult[] cradilyResults = rankAllCombos(152, 194, 200, true, 1500, false); //great league test
    @Test
    public void testRankAllCombos_Cradily_Great_League()
    {
        RankedIvResult[] rank = CpCalculator.rankAllCombos(152, 194, 200, true, 1500, false);
        RankedIvResult rankedIvResult = null;
        for (int i = 0; i < 4096; i++)
        {
            if (1 == (rank[i].statProductRank()))
            {
                rankedIvResult = rank[i];
            }
        }
        assertEquals(0, rankedIvResult.ivResult().atkIV());
        assertEquals(15, rankedIvResult.ivResult().defIV());
        assertEquals(15, rankedIvResult.ivResult().staIV());
    }

    @Test
    public void testRankAllCombos_Cradily_Ultra_League()
    {
        RankedIvResult[] rank = CpCalculator.rankAllCombos(152, 194, 200, true, 2500, true);
        RankedIvResult rankedIvResult = null;
        for (int i = 0; i < 4096; i++)
        {
            if (1 == (rank[i].statProductRank()))
            {
                rankedIvResult = rank[i];
            }
        }
        assertEquals(14, rankedIvResult.ivResult().atkIV()); //Identified previous bug here where Math.floor() was done
        assertEquals(15, rankedIvResult.ivResult().defIV()); //prematurely on hp stat, causing incorrect results
        assertEquals(15, rankedIvResult.ivResult().staIV());
    }
    
}

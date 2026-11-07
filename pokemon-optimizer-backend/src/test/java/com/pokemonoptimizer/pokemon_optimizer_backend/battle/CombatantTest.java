package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

import org.junit.jupiter.api.Test;

public class CombatantTest {

    private static final SpeciesType MEDICHAM = new SpeciesLookup().findById("medicham").orElseThrow();
    private static final double DELTA = 1e-9;

    // Medicham 121/152/155, IVs 4/14/9, level 20.5 -> CPM 0.6048236651
    private static final Combatant REGULAR = new Combatant(MEDICHAM, 4, 14, 9, 20.5, false);
    private static final Combatant SHADOW  = new Combatant(MEDICHAM, 4, 14, 9, 20.5, true);

    @Test
    public void attackIsBasePlusIvTimesCpm()
    {   // (121 + 4) * 0.6048236651
        assertEquals(75.6029581375, REGULAR.attack(), DELTA);
    }

    @Test
    public void defenseIsBasePlusIvTimesCpm()
    {   // (152 + 14) * 0.6048236651
        assertEquals(100.4007284066, REGULAR.defense(), DELTA);
    }

    @Test
    public void hpIsFlooredBasePlusIvTimesCpm()
    {   // (155 + 9) * 0.6048236651 = 99.19 -> 99
        assertEquals(99, REGULAR.maxHp());
    }

    @Test
    public void shadowHitsHarder()
    {   // 75.6029581375 * 1.2
        assertEquals(90.723549765, SHADOW.attack(), DELTA);
    }

    @Test
    public void shadowTakesMoreDamage()
    {   // 100.4007284066 * 0.83333331
        assertEquals(83.6672713295, SHADOW.defense(), DELTA);
    }

    @Test
    public void shadowDoesNotChangeHp()
    {
        assertEquals(99, SHADOW.maxHp());
    }

    @Test
    public void attackStageMultipliesAttack()
    {   // 75.6029581375 * 1.25
        assertEquals(94.503697671875, REGULAR.attack(1), DELTA);
    }

    @Test
    public void defenseStageStacksWithShadow()
    {   // 100.4007284066 * (4/6 * 0.83333331)
        assertEquals(55.778180886322, SHADOW.defense(-2), DELTA);
    }
}

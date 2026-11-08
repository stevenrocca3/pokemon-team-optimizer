package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

public class PokeGenieCsvTest
{
    // the real export's full header: the parser must find columns by name among ~50
    static final String HEADER = "Index,Name,Form,Pokemon Number,Gender,CP,HP,Atk IV,Def IV,Sta IV,IV Avg,Level Min,Level Max,"
        + "Quick Move,Charge Move,Charge Move 2,Scan Date,Original Scan Date,Catch Date,Weight,Height,Lucky,Shadow/Purified,"
        + "Favorite,Dust,Rank % (G),Rank # (G),Stat Prod (G),Dust Cost (G),Candy Cost (G),Name (G),Form (G),Sha/Pur (G),"
        + "Rank % (U),Rank # (U),Stat Prod (U),Dust Cost (U),Candy Cost (U),Name (U),Form (U),Sha/Pur (U),"
        + "Rank % (L),Rank # (L),Stat Prod (L),Dust Cost (L),Candy Cost (L),Name (L),Form (L),Sha/Pur (L),Marked for PvP use\n";

    @Test
    public void readsOneRow()
    {
        String csv = HEADER
            + "1,Swampert,,260,,1500,145,0,15,14,64.4,20.0,20.0,Mud Shot,Hydro Cannon,Earthquake,"
            + "2026-11-08 10:00,2026-11-08 10:00,11/8/2026,80kg,1.5m,0,1,0,2500,,,,,,,,,,,,,,,,,,,,,,,,,\n";

        PokeGenieImport result = PokeGenieCsv.parse(new StringReader(csv));

        assertEquals(List.of(new PokeGenieRow("Swampert", "", 0, 15, 14, 20.0, "Mud Shot", "Hydro Cannon", "Earthquake", true)), result.rows());
    }

    @Test
    public void rowWithUnknownIvsIsRejectedWithoutFailingTheFile()
    {   // Genie leaves IVs blank when the scan couldn't narrow them to one spread
        String csv = HEADER
            + "1,Swampert,,260,,1500,145,0,15,14,64.4,20.0,20.0,Mud Shot,Hydro Cannon,Earthquake,"
            + "2026-11-08 10:00,2026-11-08 10:00,11/8/2026,80kg,1.5m,0,1,0,2500,,,,,,,,,,,,,,,,,,,,,,,,,\n"
            + "2,Fennekin,,653,,259,40,,,,94.2,9.0,9.0,Ember,Flamethrower,,"
            + "2026-11-08 10:00,2026-11-08 10:00,11/8/2026,9kg,0.4m,0,0,0,800,,,,,,,,,,,,,,,,,,,,,,,,,\n";

        PokeGenieImport result = PokeGenieCsv.parse(new StringReader(csv));

        assertEquals(1, result.rows().size());                                         // Swampert still imports
        assertEquals(List.of(new RejectedRow(2, "IVs unknown")), result.rejected());   // 2 = Genie's Index column
    }
}

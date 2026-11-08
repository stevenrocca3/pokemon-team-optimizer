package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

public class CalcyIvCsvTest
{
    // a real Calcy IV history export (Nov 2026): five scans, no moves recorded
    private static Reader realExport()
    {
        return new InputStreamReader(CalcyIvCsvTest.class.getResourceAsStream("/importer/calcy-iv-history.csv"), StandardCharsets.UTF_8);
    }

    public static final String HEADER = "Ancestor?,Scan date,Nr,Name,Temp Evo,Mega Levels,Gender,Nickname,Level,possibleLevels,CP,HP,"
        + "Dust cost,min IV%,ØIV%,max IV%,ØATT IV,ØDEF IV,ØHP IV,Unique?,Fast move,Fast move (ID),Special move,Special move (ID),"
        + "Special move 2,Special move 2 (ID),DPS,LL Rank (min),LL Rank (max),GL Evo,GL Rank (min),GL Rank (max),UL Evo,UL Rank (min),"
        + "UL Rank (max),Box,Custom1,Custom2,Saved,Egg,Lucky?,Favorite,BuddyBoosted,Form,ShadowForm,MultiForm?,Dynamax,Height (cm),"
        + "Weight (g),Height Tag,Catch Date,Catch Level\n";

    /** A Medicham row; the arguments are the columns the tests vary. */
    public static String medicham(String unique, String fast, String charged, String charged2, String shadowForm)
    {
        return "0,11/8/26 16:57:32,308,Medicham,-,?,,Med,40.0,40.0,1439,140,10000,66.7,66.7,66.7,0.0,15.0,15.0," + unique + ","
            + fast + ", - ," + charged + ", - ," + charged2 + ", - ,0.0, - , - , - , - , - , - , - , - ,Default,,,0,0,0,0,0,1"
            + "," + shadowForm + ",0,D,130,31500,0,2026-?-?,?\n";
    }

    @Test
    public void readsTheRealExport() throws IOException
    {
        try (Reader csv = realExport())
        {
            PokeGenieImport result = CalcyIvCsv.parse(csv);

            assertEquals(5, result.rows().size());
            assertEquals(List.of(), result.rejected());
            assertEquals(new PokeGenieRow(1, "Metagross", "", 11, 15, 15, 40.0, "", "", "", false), result.rows().get(0));
        }
    }

    @Test
    public void regionalFormsComeOffTheEndOfTheName() throws IOException
    {   // Calcy writes "Arcanine Hisui" and "Raichu Alolan" in the Name column
        try (Reader csv = realExport())
        {
            List<PokeGenieRow> rows = CalcyIvCsv.parse(csv).rows();

            assertEquals("Arcanine", rows.get(2).name());
            assertEquals("Hisui", rows.get(2).form());
            assertEquals("Raichu", rows.get(4).name());
            assertEquals("Alolan", rows.get(4).form());
        }
    }

    @Test
    public void movesAreReadWhenCalcyRecordedThem()
    {
        String csv = HEADER + medicham("1", "Counter", "Ice Punch", "Psychic", "1");

        PokeGenieRow row = CalcyIvCsv.parse(new StringReader(csv)).rows().get(0);

        assertEquals(List.of("Counter", "Ice Punch", "Psychic"), List.of(row.fastMove(), row.chargedMove1(), row.chargedMove2()));
    }

    @Test
    public void ivsNotNarrowedToOneSpreadAreRejected()
    {   // the ØATT/ØDEF/ØHP columns are averages of every spread that fits the scan
        String csv = HEADER + medicham("1", " - ", " - ", " - ", "1") + medicham("0", " - ", " - ", " - ", "1");

        PokeGenieImport result = CalcyIvCsv.parse(new StringReader(csv));

        assertEquals(1, result.rows().size());
        assertEquals(List.of(new RejectedRow(2, "IVs not narrowed to one spread")), result.rejected());
    }

    @Test
    public void anUnknownShadowValueIsRejectedRatherThanGuessed()
    {   // 1 and 7 are both regular Pokémon in a real export; what Calcy writes for a Shadow isn't known yet
        String csv = HEADER + medicham("1", " - ", " - ", " - ", "7") + medicham("1", " - ", " - ", " - ", "2");

        PokeGenieImport result = CalcyIvCsv.parse(new StringReader(csv));

        assertEquals(1, result.rows().size());
        assertEquals(List.of(new RejectedRow(2, "Shadow or not unknown; add it by hand")), result.rejected());
    }

    @Test
    public void aFileWithoutCalcysColumnsIsRefused()
    {
        assertThrows(NotAPokeGenieExportException.class, () -> CalcyIvCsv.parse(new StringReader("name,cp\nSwampert,1500\n")));
    }
}

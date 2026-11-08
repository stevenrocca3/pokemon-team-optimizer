package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Reads a Calcy IV history export (free, unlike Poké Genie's) into the same rows a Poké Genie export
 * gives, so species and move matching is shared. Columns are found by header name.
 */
public final class CalcyIvCsv
{
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setTrim(true)
        .get();

    static final String ATTACK = "ØATT IV";

    private static final Set<String> REQUIRED = Set.of("Name", "Level", ATTACK, "ØDEF IV", "ØHP IV", "Unique?",
        "Fast move", "Special move", "Special move 2", "ShadowForm");

    // Calcy puts the region at the end of the name: "Arcanine Hisui", "Raichu Alolan"
    private static final Set<String> REGIONS = Set.of("alola", "alolan", "galar", "galarian", "hisui", "hisuian", "paldea", "paldean");

    // ShadowForm values seen on regular Pokémon in a real export; the Shadow and Purified values aren't known yet
    private static final Set<String> REGULAR = Set.of("1", "7");

    // what Calcy writes in a move column it didn't record
    private static final String NO_MOVE = "-";

    private CalcyIvCsv() {}

    public static PokeGenieImport parse(Reader csv)
    {
        try (CSVParser parser = CSVParser.parse(csv, FORMAT))
        {
            List<String> missing = REQUIRED.stream().filter(column -> !parser.getHeaderMap().containsKey(column)).sorted().toList();
            if (!missing.isEmpty())
            {
                throw new NotAPokeGenieExportException("This isn't a Calcy IV export (missing columns: " + String.join(", ", missing) + ").");
            }
            List<PokeGenieRow> rows = new ArrayList<>();
            List<RejectedRow> rejected = new ArrayList<>();
            int index = 0;
            for (CSVRecord record : parser)
            {
                index++;   // Calcy has no index column: rows are numbered in file order
                String problem = problemWith(record);
                if (problem != null)
                {
                    rejected.add(new RejectedRow(index, problem));
                    continue;
                }
                String[] nameAndForm = splitRegion(record.get("Name"));
                rows.add(new PokeGenieRow(
                    index,
                    nameAndForm[0],
                    nameAndForm[1],
                    iv(record.get(ATTACK)),
                    iv(record.get("ØDEF IV")),
                    iv(record.get("ØHP IV")),
                    Double.parseDouble(record.get("Level")),
                    move(record.get("Fast move")),
                    move(record.get("Special move")),
                    move(record.get("Special move 2")),
                    false));
            }
            return new PokeGenieImport(rows, rejected);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Couldn't read the Calcy IV CSV", e);
        }
    }

    private static String problemWith(CSVRecord record)
    {
        // the IV columns are averages over every spread that fits the scan; only a unique one is exact
        if (!"1".equals(record.get("Unique?")))
        {
            return "IVs not narrowed to one spread";
        }
        if (!REGULAR.contains(record.get("ShadowForm")))
        {
            return "Shadow or not unknown; add it by hand";
        }
        try
        {
            Double.parseDouble(record.get("Level"));
        }
        catch (NumberFormatException e)
        {
            return "Level unknown";
        }
        return null;
    }

    /** "Arcanine Hisui" → ["Arcanine", "Hisui"]; "Metagross" → ["Metagross", ""] */
    private static String[] splitRegion(String name)
    {
        int space = name.lastIndexOf(' ');
        if (space > 0 && REGIONS.contains(name.substring(space + 1).toLowerCase()))
        {
            return new String[] {name.substring(0, space), name.substring(space + 1)};
        }
        return new String[] {name, ""};
    }

    /** "11.0" → 11 */
    private static int iv(String value)
    {
        return (int) Math.round(Double.parseDouble(value));
    }

    private static String move(String value)
    {
        return value.equals(NO_MOVE) ? "" : value;
    }
}

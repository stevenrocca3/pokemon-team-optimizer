package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** Reads a Poké Genie CSV export. Columns are found by header name, so their order doesn't matter. */
public final class PokeGenieCsv
{
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
        .setHeader()                 // take column names from the first line
        .setSkipHeaderRecord(true)
        .get();

    private PokeGenieCsv() {}

    public static PokeGenieImport parse(Reader csv)
    {
        try (CSVParser parser = CSVParser.parse(csv, FORMAT))
        {
            List<PokeGenieRow> rows = new ArrayList<>();
            List<RejectedRow> rejected = new ArrayList<>();
            for (CSVRecord record : parser)
            {
                String problem = problemWith(record);
                if (problem != null)
                {
                    rejected.add(new RejectedRow(Integer.parseInt(record.get("Index")), problem));
                    continue;
                }
                rows.add(new PokeGenieRow(
                    record.get("Name"),
                    record.get("Form"),
                    Integer.parseInt(record.get("Atk IV")),
                    Integer.parseInt(record.get("Def IV")),
                    Integer.parseInt(record.get("Sta IV")),
                    Double.parseDouble(record.get("Level Min")),   // Min vs Max disagreement gets its own test
                    record.get("Quick Move"),
                    record.get("Charge Move"),
                    record.get("Charge Move 2"),
                    "1".equals(record.get("Shadow/Purified"))));   // 0 normal, 1 shadow, 2 purified
            }
            return new PokeGenieImport(rows, rejected);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Couldn't read the Poké Genie CSV", e);
        }
    }

    /** Why this row can't be imported, or null if it can. */
    private static String problemWith(CSVRecord record)
    {
        // Genie leaves IVs blank when a scan matches more than one spread
        if (record.get("Atk IV").isBlank() || record.get("Def IV").isBlank() || record.get("Sta IV").isBlank())
        {
            return "IVs unknown";
        }
        return null;
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    // every column the parser reads; a file without them isn't a Genie export
    private static final Set<String> REQUIRED = Set.of("Index", "Name", "Form", "Atk IV", "Def IV", "Sta IV", "Level Min",
        "Quick Move", "Charge Move", "Charge Move 2", "Shadow/Purified");

    private PokeGenieCsv() {}

    public static PokeGenieImport parse(Reader csv)
    {
        try (CSVParser parser = CSVParser.parse(skipByteOrderMark(csv), FORMAT))
        {
            List<String> missing = REQUIRED.stream().filter(column -> !parser.getHeaderMap().containsKey(column)).sorted().toList();
            if (!missing.isEmpty())
            {
                throw new NotAPokeGenieExportException("This isn't a Poké Genie export (missing columns: " + String.join(", ", missing) + ").");
            }
            List<PokeGenieRow> rows = new ArrayList<>();
            List<RejectedRow> rejected = new ArrayList<>();
            for (CSVRecord record : parser)
            {
                int index = Integer.parseInt(record.get("Index"));
                String problem = problemWith(record);
                if (problem != null)
                {
                    rejected.add(new RejectedRow(index, problem));
                    continue;
                }
                rows.add(new PokeGenieRow(
                    index,
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

    /** Excel puts U+FEFF before the first column name when it re-saves a CSV. */
    private static Reader skipByteOrderMark(Reader csv) throws IOException
    {
        PushbackReader reader = new PushbackReader(csv);
        int first = reader.read();
        if (first != -1 && first != '\uFEFF')
        {
            reader.unread(first);
        }
        return reader;
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

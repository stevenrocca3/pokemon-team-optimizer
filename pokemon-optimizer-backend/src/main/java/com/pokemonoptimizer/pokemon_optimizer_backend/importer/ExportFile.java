package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

import java.io.StringReader;

/** An uploaded export from either app, told apart by its header row. */
public final class ExportFile
{
    private ExportFile() {}

    public static PokeGenieImport parse(String content)
    {
        String csv = content.startsWith("﻿") ? content.substring(1) : content;   // Excel's byte order mark
        int newline = csv.indexOf('\n');
        String header = newline < 0 ? csv : csv.substring(0, newline);
        if (header.contains(CalcyIvCsv.ATTACK))
        {
            return CalcyIvCsv.parse(new StringReader(csv));
        }
        if (header.contains("Quick Move"))
        {
            return PokeGenieCsv.parse(new StringReader(csv));
        }
        throw new NotAPokeGenieExportException("This isn't a Poké Genie or Calcy IV export.");
    }
}

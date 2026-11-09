package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

/** Pulls PvPoke's current formats, stores a good pull and switches the catalog to it; logs a bad one and keeps the old. */
@Service
public class CupSync
{
    /** Good pulls that keep their formats; older ones keep only their log line. */
    static final int RUNS_KEPT = 14;

    private static final Logger log = LoggerFactory.getLogger(CupSync.class);

    private final PvpokeSource source;
    private final SpeciesLookup species;
    private final MoveLookup moves;
    private final CupSyncRunRepository runs;
    private final CupCatalog catalog;

    public CupSync(PvpokeSource source, SpeciesLookup species, MoveLookup moves, CupSyncRunRepository runs, CupCatalog catalog)
    {
        this.source = source;
        this.species = species;
        this.moves = moves;
        this.runs = runs;
        this.catalog = catalog;
    }

    /** One pull at a time: the nightly run and a startup run can't interleave. */
    public synchronized CupSyncRun sync()
    {
        Instant started = Instant.now();
        ParsedCups parsed;
        try
        {
            parsed = PvpokeCups.parse(source.gamemaster(), source::metaGroup, species, moves);
        }
        catch (RuntimeException e)
        {
            log.warn("Cup sync failed, keeping the last good Cups: {}", e.getMessage());
            return runs.save(CupSyncRun.failed(started, Instant.now(), e.getClass().getSimpleName() + ": " + e.getMessage()));
        }

        CupSyncRun run = CupSyncRun.succeeded(started, Instant.now(), parsed.sourceTimestamp(), parsed.notes());
        for (CupFormat format : parsed.formats())
        {
            run.add(new CupFormatRow(run, format, CupCatalog.JSON.writeValueAsString(format)));
        }
        CupSyncRun saved = runs.save(run);
        List<Long> older = runs.succeededIdsNewestFirst().stream().skip(RUNS_KEPT).toList();
        if (!older.isEmpty())
        {
            runs.deleteFormatsOf(older);
        }
        catalog.reload();
        log.info("Cup sync: {} formats from PvPoke {}; {} notes", parsed.formats().size(), parsed.sourceTimestamp(), parsed.notes().size());
        return saved;
    }
}

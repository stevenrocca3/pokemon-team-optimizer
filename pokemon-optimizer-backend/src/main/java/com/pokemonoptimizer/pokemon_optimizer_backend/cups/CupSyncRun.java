package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** One pull of PvPoke's data (V9): when, whether it worked, and the notes on what was left out or why it failed. */
@Entity
@Table(name = "cup_sync_run")
public class CupSyncRun
{
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cup_sync_run_ids")
    @SequenceGenerator(name = "cup_sync_run_ids", sequenceName = "cup_sync_run_seq", allocationSize = 1)
    private Long id;
    @Column(nullable = false)
    private Instant startedAt;
    @Column(nullable = false)
    private Instant finishedAt;
    @Column(nullable = false)
    private boolean succeeded;
    private String sourceTimestamp;
    @Column(nullable = false)
    private String notes;
    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL)
    private List<CupFormatRow> formats = new ArrayList<>();

    protected CupSyncRun() {}

    static CupSyncRun succeeded(Instant startedAt, Instant finishedAt, String sourceTimestamp, List<String> notes)
    {
        CupSyncRun run = new CupSyncRun();
        run.startedAt = startedAt;
        run.finishedAt = finishedAt;
        run.succeeded = true;
        run.sourceTimestamp = sourceTimestamp;
        run.notes = String.join("\n", notes);
        return run;
    }

    static CupSyncRun failed(Instant startedAt, Instant finishedAt, String why)
    {
        CupSyncRun run = new CupSyncRun();
        run.startedAt = startedAt;
        run.finishedAt = finishedAt;
        run.succeeded = false;
        run.notes = why;
        return run;
    }

    void add(CupFormatRow format)
    {
        formats.add(format);
    }

    public Long getId() { return id; }
    public Instant getFinishedAt() { return finishedAt; }
    public boolean isSucceeded() { return succeeded; }
    public String getSourceTimestamp() { return sourceTimestamp; }
    public String getNotes() { return notes; }
    public List<CupFormatRow> getFormats() { return formats; }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** One format from a good pull (V9). {@code payload} is the whole {@link CupFormat} as JSON. */
@Entity
@Table(name = "cup_format")
public class CupFormatRow
{
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cup_format_ids")
    @SequenceGenerator(name = "cup_format_ids", sequenceName = "cup_format_seq", allocationSize = 50)
    private Long id;
    @ManyToOne(optional = false)
    private CupSyncRun run;
    @Column(nullable = false)
    private String formatKey;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private int cpCap;
    @Column(nullable = false)
    private String payload;

    protected CupFormatRow() {}

    CupFormatRow(CupSyncRun run, CupFormat format, String payload)
    {
        this.run = run;
        this.formatKey = format.key();
        this.title = format.title();
        this.cpCap = format.cpCap();
        this.payload = payload;
    }

    public String getFormatKey() { return formatKey; }
    public String getPayload() { return payload; }
}

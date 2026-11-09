package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Pulls the Cups every night, and once at startup when the last good pull is a day old (or there's none yet, as on
 * a fresh database). Off in tests (app.cups.sync=false), which must not reach the internet.
 */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty("app.cups.sync")
public class CupSyncSchedule
{
    private static final Duration STALE = Duration.ofHours(20);

    private final CupSync sync;
    private final CupCatalog catalog;

    public CupSyncSchedule(CupSync sync, CupCatalog catalog)
    {
        this.sync = sync;
        this.catalog = catalog;
    }

    @Scheduled(cron = "${app.cups.sync-cron}", zone = "UTC")
    public void nightly()
    {
        sync.sync();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)   // after CupCatalog has loaded the last good pull
    public void onStartup()
    {
        boolean stale = catalog.lastSynced().map(at -> at.isBefore(Instant.now().minus(STALE))).orElse(true);
        if (stale)
        {
            Thread.ofVirtual().name("cup-sync-startup").start(sync::sync);   // don't hold up startup on GitHub
        }
    }
}

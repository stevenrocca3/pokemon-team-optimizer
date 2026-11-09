package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Combatant;
import com.pokemonoptimizer.pokemon_optimizer_backend.battle.Moveset;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.MetaPokemon;

import tools.jackson.databind.json.JsonMapper;

/** The Cups from the newest good pull, built once into battle-ready meta and kept in memory until the next pull. */
@Component
public class CupCatalog
{
    private static final Logger log = LoggerFactory.getLogger(CupCatalog.class);
    static final JsonMapper JSON = new JsonMapper();

    private record Snapshot(List<Cup> cups, Instant syncedAt) {}

    private final CupSyncRunRepository runs;
    private final SpeciesLookup species;
    private final MoveLookup moves;
    private volatile Snapshot current = new Snapshot(List.of(), null);

    public CupCatalog(CupSyncRunRepository runs, SpeciesLookup species, MoveLookup moves)
    {
        this.runs = runs;
        this.species = species;
        this.moves = moves;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)   // before CupSyncSchedule decides whether a pull is due
    public void onStartup()
    {
        reload();
    }

    /** Reads the newest good pull again; called at startup and after every sync. */
    public void reload()
    {
        runs.findFirstBySucceededTrueOrderByIdDesc().ifPresent(run -> {
            List<Cup> cups = new ArrayList<>();
            for (CupFormatRow row : run.getFormats())
            {
                try
                {
                    cups.add(build(JSON.readValue(row.getPayload(), CupFormat.class), run.getSourceTimestamp()));
                }
                catch (RuntimeException e)   // a stored format the current code can't read: leave it out, keep the rest
                {
                    log.warn("Couldn't build stored Cup {}: {}", row.getFormatKey(), e.getMessage());
                }
            }
            cups.sort((a, b) -> a.title().compareToIgnoreCase(b.title()));
            current = new Snapshot(List.copyOf(cups), run.getFinishedAt());
        });
    }

    public List<Cup> cups()
    {
        return current.cups();
    }

    public Optional<Cup> find(String key)
    {
        return current.cups().stream().filter(cup -> cup.key().equals(key)).findFirst();
    }

    /** When the Cups in use were pulled; empty if there has never been a good pull. */
    public Optional<Instant> lastSynced()
    {
        return Optional.ofNullable(current.syncedAt());
    }

    private Cup build(CupFormat format, String sourceTimestamp)
    {
        List<MetaPokemon> meta = new ArrayList<>();
        for (MetaEntry entry : format.meta())
        {
            Optional<SpeciesType> found = species.findById(entry.speciesId());
            Optional<Move> fast = moves.findById(entry.fastMove());
            List<Move> charged = entry.chargedMoves().stream().map(moves::findById).flatMap(Optional::stream).toList();
            if (found.isEmpty() || fast.isEmpty() || charged.size() != entry.chargedMoves().size())
            {
                continue;   // the species data changed since the pull
            }
            Combatant combatant = new Combatant(found.get(), entry.atkIv(), entry.defIv(), entry.staIv(), entry.level(), entry.shadow());
            meta.add(new MetaPokemon(entry.shadow() ? entry.speciesId() + "_shadow" : entry.speciesId(), combatant,
                new Moveset(fast.get(), charged)));
        }
        String updated = sourceTimestamp == null ? null : sourceTimestamp.substring(0, Math.min(10, sourceTimestamp.length()));
        return new Cup(format.key(), format.title(), League.ofPvpokeCap(format.cpCap()), format.cpCap(), format.partySize(),
            format.rules(), format.eligibility(), List.copyOf(meta), updated);
    }
}

package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.RawGamemaster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder.League;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class CupSyncTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();

    @Autowired private CupSyncRunRepository runs;

    private CupFixtures.FakePvpoke pvpoke;
    private CupCatalog catalog;
    private CupSync sync;

    @BeforeEach
    public void setUp()
    {
        pvpoke = new CupFixtures.FakePvpoke();
        catalog = new CupCatalog(runs, SPECIES, MOVES);
        sync = new CupSync(pvpoke, SPECIES, MOVES, runs, catalog);
    }

    @Test
    public void aGoodPullIsStoredAndBecomesTheCatalog()
    {
        CupSyncRun run = sync.sync();

        assertThat(run.isSucceeded()).isTrue();
        assertThat(run.getSourceTimestamp()).isEqualTo(RawGamemaster.timestamp());
        Cup fantasy = catalog.find("fantasy-1500").orElseThrow();
        assertThat(fantasy.title()).isEqualTo("Fantasy Cup");
        assertThat(fantasy.league()).isEqualTo(League.GREAT);
        assertThat(fantasy.meta()).hasSize(44);
        assertThat(fantasy.meta().get(1).id()).isEqualTo("annihilape_shadow");
        assertThat(fantasy.meta().get(1).combatant().level()).isEqualTo(17);
        assertThat(fantasy.updated()).isEqualTo(RawGamemaster.timestamp().substring(0, 10));
        assertThat(catalog.lastSynced()).contains(run.getFinishedAt());
    }

    @Test
    public void theCatalogSurvivesARestart()
    {
        sync.sync();

        CupCatalog restarted = new CupCatalog(runs, SPECIES, MOVES);
        restarted.reload();

        assertThat(restarted.find("fantasy-1500")).isPresent();
    }

    @Test
    public void aBadPullIsLoggedAndYesterdaysCupsStay()
    {
        sync.sync();
        pvpoke.broken = true;

        CupSyncRun failed = sync.sync();

        assertThat(failed.isSucceeded()).isFalse();
        assertThat(failed.getNotes()).contains("timed out");
        assertThat(runs.findById(failed.getId())).isPresent();
        assertThat(catalog.find("fantasy-1500")).isPresent();
    }

    @Test
    public void olderGoodPullsLoseTheirFormatsButKeepTheirLogLine()
    {
        CupSyncRun first = sync.sync();
        for (int i = 0; i < CupSync.RUNS_KEPT; i++)
        {
            sync.sync();
        }

        CupSyncRun reread = runs.findById(first.getId()).orElseThrow();
        assertThat(reread.getFormats()).isEmpty();
        assertThat(runs.findFirstBySucceededTrueOrderByIdDesc().orElseThrow().getFormats()).hasSize(1);
    }

    @Test
    public void anUnknownCupIsntFound()
    {
        sync.sync();

        assertThat(catalog.find("worlds-2099")).isEmpty();
    }
}

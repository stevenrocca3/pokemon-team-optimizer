package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface CupSyncRunRepository extends JpaRepository<CupSyncRun, Long>
{
    /** The newest good pull, with its formats. */
    @EntityGraph(attributePaths = "formats")
    Optional<CupSyncRun> findFirstBySucceededTrueOrderByIdDesc();

    @Query("select r.id from CupSyncRun r where r.succeeded = true order by r.id desc")
    List<Long> succeededIdsNewestFirst();

    /** Older pulls keep their log line but not their formats. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CupFormatRow f where f.run.id in :runIds")
    void deleteFormatsOf(List<Long> runIds);
}

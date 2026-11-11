package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;

import tools.jackson.databind.JsonNode;

/**
 * Saves finished battle analyses to their player's history. The browser reports a job when it sees it finish; the
 * result is fetched here from the analysis service, never taken from the browser, so a score can't be forged. The
 * job id (128 random bits, known only to whoever uploaded the video) is what proves the battle is theirs.
 */
@Service
public class BattleHistoryService
{
    private static final Pattern JOB_ID = Pattern.compile("[0-9a-f]{32}");

    private final BattleLogRepository battleLogs;
    private final MatchHistoryRepository history;
    private final TrainerRepository trainers;
    private final AnalysisResults results;
    private final Clock clock;

    @Autowired
    public BattleHistoryService(BattleLogRepository battleLogs, MatchHistoryRepository history,
                                TrainerRepository trainers, AnalysisResults results)
    {
        this(battleLogs, history, trainers, results, Clock.systemUTC());
    }

    BattleHistoryService(BattleLogRepository battleLogs, MatchHistoryRepository history, TrainerRepository trainers,
                         AnalysisResults results, Clock clock)
    {
        this.battleLogs = battleLogs;
        this.history = history;
        this.trainers = trainers;
        this.results = results;
        this.clock = clock;
    }

    public static boolean isJobId(String jobId)
    {
        return jobId != null && JOB_ID.matcher(jobId).matches();
    }

    /**
     * Saves the job's battle to the owner's history. True when it was saved now; false when it was already theirs
     * (reported twice). A battle saved under the owner's guest token moves to their account when they're logged in.
     *
     * @throws BattleOwnedElsewhereException it's already in someone else's history
     */
    @Transactional
    public boolean record(String jobId, BattleOwner owner)
    {
        if (!isJobId(jobId))
        {
            throw new AnalysisNotFoundException();
        }
        Trainer trainer = owner.googleId() == null ? null : trainers.findByGoogleId(owner.googleId()).orElseThrow();
        Optional<MatchHistory> saved = history.findByBattleMatchId(matchId(jobId));
        if (saved.isPresent())
        {
            MatchHistory row = saved.get();
            boolean trainersOwn = trainer != null && row.getTrainer() != null && Objects.equals(row.getTrainer().getId(), trainer.getId());
            boolean guestsOwn = owner.guestToken() != null && owner.guestToken().equals(row.getGuestToken());
            if (!trainersOwn && !guestsOwn)
            {
                throw new BattleOwnedElsewhereException();
            }
            if (guestsOwn && trainer != null)
            {
                row.giveTo(trainer);
            }
            return false;
        }

        BattleLog log = battleLogs.save(fromResult(jobId, results.result(jobId)));
        history.save(new MatchHistory(log, trainer, trainer == null ? owner.guestToken() : null, clock.instant()));
        return true;
    }

    /** After logging in: the battles saved under this browser's guest token become the Trainer's. How many moved. */
    @Transactional
    public int claim(String googleId, UUID guestToken)
    {
        Trainer trainer = trainers.findByGoogleId(googleId).orElseThrow();
        List<MatchHistory> guests = history.findByGuestToken(guestToken);
        guests.forEach(row -> row.giveTo(trainer));
        return guests.size();
    }

    static UUID matchId(String jobId)
    {
        return UUID.fromString(jobId.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }

    /** The record's fields from the analysis result: {turn_log: {user_team, opponent_team, ...}, report: {...}}. */
    private BattleLog fromResult(String jobId, JsonNode result)
    {
        JsonNode log = result.path("turn_log");
        JsonNode report = result.path("report");
        JsonNode counts = report.path("counts");
        JsonNode metrics = report.path("history");
        return new BattleLog(
            matchId(jobId),
            clock.instant(),
            names(log.path("user_team")),
            names(log.path("opponent_team")),
            BigDecimal.valueOf(report.path("score").asDouble()),
            report.path("tier").path("key").asString(),
            textOrNull(report.path("outcome")),
            metrics.path("turns_lost").isNumber() ? metrics.path("turns_lost").asInt() : null,
            decimalOrNull(metrics.path("shield_efficiency_pct")),
            decimalOrNull(metrics.path("timing_accuracy_pct")),
            counts.path("timing_errors").asInt(),
            counts.path("mismanaged_shields").asInt(),
            counts.path("suboptimal_switches").asInt(),
            counts.path("suboptimal_charges").asInt(),
            result.toString());
    }

    private static List<String> names(JsonNode array)
    {
        return array.valueStream().map(JsonNode::asString).toList();
    }

    private static String textOrNull(JsonNode node)
    {
        return node.isString() ? node.asString() : null;
    }

    private static BigDecimal decimalOrNull(JsonNode node)
    {
        return node.isNumber() ? node.decimalValue() : null;
    }
}

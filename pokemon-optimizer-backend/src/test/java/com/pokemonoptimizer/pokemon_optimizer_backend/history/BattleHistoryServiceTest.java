package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.pokemonoptimizer.pokemon_optimizer_backend.account.AccountService;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Issue #120: every finished analysis is saved to its player's history, a Trainer's or a guest's. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AccountService.class)
public class BattleHistoryServiceTest
{
    private static final JsonMapper JSON = new JsonMapper();
    private static final UUID GUEST = UUID.fromString("7f0c2c1e-4b8a-4d5e-9a61-2f3b4c5d6e7f");
    private static final UUID OTHER_GUEST = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

    @Autowired private BattleLogRepository battleLogs;
    @Autowired private MatchHistoryRepository history;
    @Autowired private TrainerRepository trainers;
    @Autowired private AccountService accountService;
    @Autowired private TestEntityManager entityManager;

    /** The analysis service, faked: the jobs it has finished, by id. */
    private final Map<String, JsonNode> finished = new HashMap<>();
    private BattleHistoryService service;
    private Trainer ash;

    @BeforeEach
    void setUp()
    {
        ash = entityManager.persist(new Trainer("google-ash"));
        entityManager.persist(new Trainer("google-misty"));
        AnalysisResults results = jobId -> {
            if (jobId.startsWith("dead")) throw new AnalysisNotFinishedException();
            JsonNode result = finished.get(jobId);
            if (result == null) throw new AnalysisNotFoundException();
            return result;
        };
        service = new BattleHistoryService(battleLogs, history, trainers, results);
    }

    /** A job id the fake service has finished, with rec 2's real result (or one tweaked by `edit`). */
    private String finishedJob(java.util.function.Consumer<ObjectNode> edit)
    {
        String jobId = UUID.randomUUID().toString().replace("-", "");
        try (InputStream in = getClass().getResourceAsStream("/battle-history/rec2_result.json"))
        {
            ObjectNode result = (ObjectNode) JSON.readTree(in);
            edit.accept(result);
            finished.put(jobId, result);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
        return jobId;
    }

    private String finishedJob()
    {
        return finishedJob(r -> {});
    }

    private static UUID uuid(String jobId)
    {
        return UUID.fromString(jobId.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }

    private void flush()
    {
        entityManager.flush();
        entityManager.clear();
    }

    // A logged-in Trainer's finished analysis is saved with what the spec's record holds, read from rec 2's real
    // result: both teams, the score (61), the tier, the outcome (a win), the three metrics (4 turns lost, 66.7% timing
    // accuracy, no shield used so no shield efficiency), the mistake counts, and the whole result for the review.
    @Test
    public void savesAFinishedAnalysisToTheTrainersHistory()
    {
        String jobId = finishedJob();
        assertThat(service.record(jobId, BattleOwner.trainer("google-ash"))).isTrue();
        flush();

        BattleLog log = battleLogs.findById(uuid(jobId)).orElseThrow();
        assertThat(log.getDetectedUserTeam()).containsExactly("Swampert", "Cradily", "Clodsire");
        assertThat(log.getDetectedOppTeam()).containsExactly("Morpeko", "Gastrodon", "Doublade");
        assertThat(log.getOverallExecutionScore()).isEqualByComparingTo("61");
        assertThat(log.getTier()).isEqualTo("ace");
        assertThat(log.getOutcome()).isEqualTo("WIN");
        assertThat(log.getTurnsLost()).isEqualTo(4);
        assertThat(log.getTimingAccuracyPct()).isEqualByComparingTo("66.7");
        assertThat(log.getShieldEfficiencyPct()).isNull();
        assertThat(List.of(log.getTimingErrors(), log.getMismanagedShields(), log.getSuboptimalSwitches(), log.getSuboptimalCharges()))
            .containsExactly(2, 0, 1, 0);
        assertThat(log.getResult()).contains("\"turn_log\"").contains("\"report\"");
        assertThat(log.getCreatedAt()).isNotNull();

        MatchHistory owner = history.findByBattleMatchId(uuid(jobId)).orElseThrow();
        assertThat(owner.getTrainer().getId()).isEqualTo(ash.getId());
        assertThat(owner.getGuestToken()).isNull();
    }

    // A guest's analysis is saved under their session token, with no Trainer.
    @Test
    public void savesAGuestsAnalysisUnderTheirToken()
    {
        String jobId = finishedJob();
        assertThat(service.record(jobId, BattleOwner.guest(GUEST))).isTrue();
        flush();

        MatchHistory owner = history.findByBattleMatchId(uuid(jobId)).orElseThrow();
        assertThat(owner.getTrainer()).isNull();
        assertThat(owner.getGuestToken()).isEqualTo(GUEST);
    }

    // The browser may report the same job twice (a reload, two tabs): the second is a no-op, one record stays.
    @Test
    public void recordingTheSameJobTwiceKeepsOneRecord()
    {
        String jobId = finishedJob();
        service.record(jobId, BattleOwner.trainer("google-ash"));
        flush();
        assertThat(service.record(jobId, BattleOwner.trainer("google-ash"))).isFalse();
        flush();

        assertThat(history.findByTrainerOrderByRecordedAtAsc(ash)).hasSize(1);
    }

    // Someone else reporting a job that's already in another player's history can't take it over.
    @Test
    public void anotherPlayerCantTakeOverASavedBattle()
    {
        String jobId = finishedJob();
        service.record(jobId, BattleOwner.trainer("google-ash"));
        flush();

        assertThatThrownBy(() -> service.record(jobId, BattleOwner.trainer("google-misty")))
            .isInstanceOf(BattleOwnedElsewhereException.class);
        assertThatThrownBy(() -> service.record(jobId, BattleOwner.guest(OTHER_GUEST)))
            .isInstanceOf(BattleOwnedElsewhereException.class);
        flush();
        assertThat(history.findByBattleMatchId(uuid(jobId)).orElseThrow().getTrainer().getId()).isEqualTo(ash.getId());
    }

    // A job saved while logged out, reported again after logging in (with the same guest token), moves to the account.
    @Test
    public void aGuestBattleReportedAgainAfterLoginMovesToTheAccount()
    {
        String jobId = finishedJob();
        service.record(jobId, BattleOwner.guest(GUEST));
        flush();
        service.record(jobId, new BattleOwner("google-ash", GUEST));
        flush();

        assertThat(history.findByBattleMatchId(uuid(jobId))).isPresent();
        MatchHistory owner = history.findByBattleMatchId(uuid(jobId)).orElseThrow();
        assertThat(owner.getTrainer().getId()).isEqualTo(ash.getId());
        assertThat(owner.getGuestToken()).isNull();
    }

    // A job that isn't finished, or that the analysis service doesn't know, saves nothing and says why.
    @Test
    public void unfinishedOrUnknownJobsSaveNothing()
    {
        long before = battleLogs.count();
        assertThatThrownBy(() -> service.record("dead" + "0".repeat(28), BattleOwner.guest(GUEST)))
            .isInstanceOf(AnalysisNotFinishedException.class);
        assertThatThrownBy(() -> service.record("f".repeat(32), BattleOwner.guest(GUEST)))
            .isInstanceOf(AnalysisNotFoundException.class);
        flush();
        assertThat(battleLogs.count()).isEqualTo(before);
    }

    // A result from before the report carried an outcome and metrics still saves, with those left empty.
    @Test
    public void aResultWithoutOutcomeOrMetricsStillSaves()
    {
        String jobId = finishedJob(r -> {
            ((ObjectNode) r.get("report")).remove("outcome");
            ((ObjectNode) r.get("report")).remove("history");
        });
        service.record(jobId, BattleOwner.guest(GUEST));
        flush();

        assertThat(battleLogs.findById(uuid(jobId))).isPresent();
        BattleLog log = battleLogs.findById(uuid(jobId)).orElseThrow();
        assertThat(log.getOutcome()).isNull();
        assertThat(log.getTurnsLost()).isNull();
        assertThat(log.getTimingAccuracyPct()).isNull();
        assertThat(log.getOverallExecutionScore()).isEqualByComparingTo(BigDecimal.valueOf(61));
    }

    // Logging in claims the guest's battles: all of that token's battles move to the Trainer; another guest's stay put.
    @Test
    public void claimingMovesTheGuestsBattlesToTheTrainer()
    {
        String first = finishedJob();
        String second = finishedJob();
        String someoneElses = finishedJob();
        service.record(first, BattleOwner.guest(GUEST));
        service.record(second, BattleOwner.guest(GUEST));
        service.record(someoneElses, BattleOwner.guest(OTHER_GUEST));
        flush();

        assertThat(service.claim("google-ash", GUEST)).isEqualTo(2);
        flush();

        assertThat(history.findByTrainerOrderByRecordedAtAsc(ash)).extracting(h -> h.getBattle().getMatchId())
            .containsExactlyInAnyOrder(uuid(first), uuid(second));
        assertThat(history.findByGuestToken(GUEST)).isEmpty();
        assertThat(history.findByGuestToken(OTHER_GUEST)).hasSize(1);
    }

    // Deleting an account deletes its battle history too (right to be forgotten); other players' battles stay.
    @Test
    public void deletingTheAccountDeletesItsBattles()
    {
        String ashs = finishedJob();
        String mistys = finishedJob();
        service.record(ashs, BattleOwner.trainer("google-ash"));
        service.record(mistys, BattleOwner.trainer("google-misty"));
        flush();

        accountService.deleteAccount("google-ash");
        flush();

        assertThat(battleLogs.findById(uuid(ashs))).isEmpty();
        assertThat(history.findByBattleMatchId(uuid(ashs))).isEmpty();
        assertThat(battleLogs.findById(uuid(mistys))).isPresent();
    }
}

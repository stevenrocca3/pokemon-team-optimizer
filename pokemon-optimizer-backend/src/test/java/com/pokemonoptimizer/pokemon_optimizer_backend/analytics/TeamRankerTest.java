package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.history.BattleRow;

/** Issue #123: a player's top 3 teams, by average execution score (60%) and estimated win rate (40%). */
public class TeamRankerTest
{
    private static final List<String> SWAMPERT = List.of("Swampert", "Cradily", "Clodsire");
    private static final List<String> MEDICHAM = List.of("Medicham", "Lickitung", "Noctowl");
    private static final List<String> AZUMARILL = List.of("Azumarill", "Registeel", "Trevenant");
    private static final List<String> SABLEYE = List.of("Sableye", "Bastiodon", "Talonflame");

    /** A history, oldest first as written, handed over newest first as read. */
    private final List<BattleRow> oldestFirst = new ArrayList<>();

    private TeamRankerTest battle(List<String> team, double score, String outcome)
    {
        oldestFirst.add(new BattleRow(UUID.randomUUID(), Instant.EPOCH.plusSeconds(oldestFirst.size()), team,
            List.of("Morpeko", "Gastrodon", "Doublade"), BigDecimal.valueOf(score), "ace", outcome, 0, null, null, 0, 0, 0, 0));
        return this;
    }

    private TeamRankerTest battles(List<String> team, int count, double score, String outcome)
    {
        for (int i = 0; i < count; i++)
        {
            battle(team, score, outcome);
        }
        return this;
    }

    private List<TeamRanking> top()
    {
        return TeamRanker.topTeams(oldestFirst.reversed());
    }

    // The same three Pokémon are one team whatever the lead: three battles with Swampert, Cradily or Clodsire in front
    // make one 3-battle team, shown in its latest battle's order.
    @Test
    public void theSameThreeInAnyOrderAreOneTeam()
    {
        battle(SWAMPERT, 70, "WIN");
        battle(List.of("Cradily", "Swampert", "Clodsire"), 70, "WIN");
        battle(List.of("Clodsire", "Cradily", "Swampert"), 70, "WIN");

        List<TeamRanking> top = top();

        assertThat(top).hasSize(1);
        assertThat(top.get(0).battles()).isEqualTo(3);
        assertThat(top.get(0).team()).containsExactly("Clodsire", "Cradily", "Swampert");
    }

    // A team needs 3 logged battles to be ranked (spec §2.2); two isn't enough.
    @Test
    public void aTeamNeedsThreeBattles()
    {
        battles(SWAMPERT, 2, 95, "WIN");
        battles(MEDICHAM, 3, 60, "LOSS");

        assertThat(top()).extracting(TeamRanking::team).containsExactly(MEDICHAM);
    }

    // Battles where fewer than three of your Pokémon came out don't say which team it was: they're left out.
    @Test
    public void battlesWithoutAFullTeamAreLeftOut()
    {
        battles(List.of("Swampert", "Cradily"), 5, 90, "WIN");

        assertThat(top()).isEmpty();
    }

    // The composite: 80 average with 2 wins and 1 loss. Win rate is estimated as (2 + 1) / (3 + 2) = 60%, so the
    // rank score is 80 × 0.6 + 60 × 0.4 = 72.0; the stats it's built from come with it.
    @Test
    public void rankScoreIsSixtyPercentExecutionFortyPercentWinRate()
    {
        battle(SWAMPERT, 80, "WIN").battle(SWAMPERT, 80, "WIN").battle(SWAMPERT, 80, "LOSS");

        TeamRanking team = top().get(0);

        assertThat(team.wins()).isEqualTo(2);
        assertThat(team.losses()).isEqualTo(1);
        assertThat(team.averageScore()).isEqualByComparingTo("80.0");
        assertThat(team.winRatePct()).isEqualByComparingTo("60.0");
        assertThat(team.rankScore()).isEqualByComparingTo("72.0");
    }

    // Recordings that stop early have no result: a team with none known is estimated at 50%, not 0% or 100%.
    @Test
    public void unknownResultsAreEstimatedAtFiftyPercent()
    {
        battles(SWAMPERT, 3, 70, null);

        TeamRanking team = top().get(0);

        assertThat(team.winRatePct()).isEqualByComparingTo("50.0");
        assertThat(team.rankScore()).isEqualByComparingTo("62.0");
    }

    // Results count, not just execution: 90 average but 0 wins in 3 (20% estimated, 62.0) ranks below 80 with 2-1 (72.0).
    @Test
    public void resultsCanOutrankExecution()
    {
        battles(MEDICHAM, 3, 90, "LOSS");
        battle(SWAMPERT, 80, "WIN").battle(SWAMPERT, 80, "WIN").battle(SWAMPERT, 80, "LOSS");

        assertThat(top()).extracting(TeamRanking::team).containsExactly(SWAMPERT, MEDICHAM);
        assertThat(top().get(1).rankScore()).isEqualByComparingTo("62.0");
    }

    // Only the best three come back, best first.
    @Test
    public void onlyTheTopThreeBestFirst()
    {
        battles(SABLEYE, 3, 40, "LOSS");
        battles(MEDICHAM, 3, 70, "WIN");
        battles(AZUMARILL, 3, 60, "WIN");
        battles(SWAMPERT, 3, 90, "WIN");

        assertThat(top()).extracting(TeamRanking::team).containsExactly(SWAMPERT, MEDICHAM, AZUMARILL);
    }

    // No battles, no teams.
    @Test
    public void noBattlesNoTeams()
    {
        assertThat(top()).isEmpty();
    }
}

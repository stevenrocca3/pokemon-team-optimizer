package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One finished battle analysis (V12): what the analysis saw and scored, and its whole result. */
@Entity
@Table(name = "battle_logs")
public class BattleLog
{
    @Id
    private UUID matchId;
    @Column(nullable = false)
    private Instant createdAt;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> detectedUserTeam;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> detectedOppTeam;
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal overallExecutionScore;
    @Column(nullable = false)
    private String tier;
    private String outcome;
    private Integer turnsLost;
    @Column(precision = 4, scale = 1)
    private BigDecimal shieldEfficiencyPct;
    @Column(precision = 4, scale = 1)
    private BigDecimal timingAccuracyPct;
    private int timingErrors;
    private int mismanagedShields;
    private int suboptimalSwitches;
    private int suboptimalCharges;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String result;

    protected BattleLog() {}

    public BattleLog(UUID matchId, Instant createdAt, List<String> detectedUserTeam, List<String> detectedOppTeam,
              BigDecimal overallExecutionScore, String tier, String outcome, Integer turnsLost,
              BigDecimal shieldEfficiencyPct, BigDecimal timingAccuracyPct, int timingErrors, int mismanagedShields,
              int suboptimalSwitches, int suboptimalCharges, String result)
    {
        this.matchId = matchId;
        this.createdAt = createdAt;
        this.detectedUserTeam = detectedUserTeam;
        this.detectedOppTeam = detectedOppTeam;
        this.overallExecutionScore = overallExecutionScore;
        this.tier = tier;
        this.outcome = outcome;
        this.turnsLost = turnsLost;
        this.shieldEfficiencyPct = shieldEfficiencyPct;
        this.timingAccuracyPct = timingAccuracyPct;
        this.timingErrors = timingErrors;
        this.mismanagedShields = mismanagedShields;
        this.suboptimalSwitches = suboptimalSwitches;
        this.suboptimalCharges = suboptimalCharges;
        this.result = result;
    }

    public UUID getMatchId() { return matchId; }
    public Instant getCreatedAt() { return createdAt; }
    public List<String> getDetectedUserTeam() { return detectedUserTeam; }
    public List<String> getDetectedOppTeam() { return detectedOppTeam; }
    public BigDecimal getOverallExecutionScore() { return overallExecutionScore; }
    public String getTier() { return tier; }
    public String getOutcome() { return outcome; }
    public Integer getTurnsLost() { return turnsLost; }
    public BigDecimal getShieldEfficiencyPct() { return shieldEfficiencyPct; }
    public BigDecimal getTimingAccuracyPct() { return timingAccuracyPct; }
    public int getTimingErrors() { return timingErrors; }
    public int getMismanagedShields() { return mismanagedShields; }
    public int getSuboptimalSwitches() { return suboptimalSwitches; }
    public int getSuboptimalCharges() { return suboptimalCharges; }
    public String getResult() { return result; }
}

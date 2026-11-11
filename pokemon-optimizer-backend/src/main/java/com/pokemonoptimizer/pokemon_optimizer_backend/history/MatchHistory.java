package com.pokemonoptimizer.pokemon_optimizer_backend.history;

import java.time.Instant;
import java.util.UUID;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Whose battle it is (V12): a Trainer's, or a guest's session token until they log in and claim it. */
@Entity
@Table(name = "user_match_history")
public class MatchHistory
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false, unique = true)
    private BattleLog battle;
    @ManyToOne
    private Trainer trainer;
    private UUID guestToken;
    @Column(nullable = false)
    private Instant recordedAt;

    protected MatchHistory() {}

    public MatchHistory(BattleLog battle, Trainer trainer, UUID guestToken, Instant recordedAt)
    {
        this.battle = battle;
        this.trainer = trainer;
        this.guestToken = guestToken;
        this.recordedAt = recordedAt;
    }

    public BattleLog getBattle() { return battle; }
    public Trainer getTrainer() { return trainer; }
    public UUID getGuestToken() { return guestToken; }
    public Instant getRecordedAt() { return recordedAt; }

    /** A guest's battle becomes the Trainer's once they log in. */
    void giveTo(Trainer trainer)
    {
        this.trainer = trainer;
        this.guestToken = null;
    }
}

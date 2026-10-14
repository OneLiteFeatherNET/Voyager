package net.elytrarace.voyager.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One finished cup, written once. Table {@code cup_session}. */
@Entity
@Table(name = "cup_session")
public class CupSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cup_session_id", nullable = false)
    private Long cupSessionId;

    @Column(name = "cup_key", nullable = false)
    private String cupKey;

    @Column(name = "game_mode", nullable = false, length = 16)
    private String gameMode;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "racer_count", nullable = false)
    private int racerCount;

    protected CupSessionEntity() {
    }
}

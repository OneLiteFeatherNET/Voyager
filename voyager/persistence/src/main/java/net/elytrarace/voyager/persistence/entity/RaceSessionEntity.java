package net.elytrarace.voyager.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * One finished map. The cup is referenced by its key column only, never by a field of the cup entity. Table
 * {@code race_session}.
 */
@Entity
@Table(name = "race_session")
public class RaceSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "race_session_id", nullable = false)
    private Long raceSessionId;

    @Column(name = "map_key", nullable = false)
    private String mapKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "layout_version", nullable = false, length = 16)
    private String layoutVersion;

    @Column(name = "game_mode", nullable = false, length = 16)
    private String gameMode;

    @Nullable
    @Column(name = "cup_session_id")
    private Long cupSessionId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "human_count", nullable = false)
    private int humanCount;

    @Column(name = "bot_count", nullable = false)
    private int botCount;

    protected RaceSessionEntity() {
    }
}

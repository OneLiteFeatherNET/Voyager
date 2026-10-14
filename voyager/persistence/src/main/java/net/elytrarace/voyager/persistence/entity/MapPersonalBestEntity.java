package net.elytrarace.voyager.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The fastest finish of one player on one map, layout and mode. Written by the upsert of design D4, which keeps the
 * minimum time. Table {@code map_personal_best}.
 */
@Entity
@Table(name = "map_personal_best")
public class MapPersonalBestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_personal_best_id", nullable = false)
    private Long mapPersonalBestId;

    @Column(name = "map_key", nullable = false)
    private String mapKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "layout_version", nullable = false, length = 16)
    private String layoutVersion;

    @Column(name = "game_mode", nullable = false, length = 16)
    private String gameMode;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "best_time_millis", nullable = false)
    private int bestTimeMillis;

    @Column(name = "race_result_id", nullable = false)
    private long raceResultId;

    @Column(name = "achieved_at", nullable = false)
    private Instant achievedAt;

    protected MapPersonalBestEntity() {
    }
}

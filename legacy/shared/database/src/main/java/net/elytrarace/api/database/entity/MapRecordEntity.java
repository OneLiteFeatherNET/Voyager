package net.elytrarace.api.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores the all-time fastest completion time for a single (cup, map) combination.
 * One row per map; updated in-place when a new record is set.
 */
@Entity
@Table(
        name = "map_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_map_records_cup_map",
                columnNames = {"cup_name", "map_name"}))
public class MapRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cup_name", nullable = false)
    private String cupName;

    @Column(name = "map_name", nullable = false)
    private String mapName;

    @Column(name = "record_time_ms", nullable = false)
    private long recordTimeMs;

    @Column(name = "holder_player_id", nullable = false)
    private UUID holderPlayerId;

    @Column(name = "achieved_at", nullable = false)
    private LocalDateTime achievedAt;

    public MapRecordEntity() {}

    public MapRecordEntity(String cupName, String mapName, long recordTimeMs,
                           UUID holderPlayerId, LocalDateTime achievedAt) {
        this.cupName = cupName;
        this.mapName = mapName;
        this.recordTimeMs = recordTimeMs;
        this.holderPlayerId = holderPlayerId;
        this.achievedAt = achievedAt;
    }

    public Long getId() { return id; }

    public String getCupName() { return cupName; }

    public String getMapName() { return mapName; }

    public long getRecordTimeMs() { return recordTimeMs; }

    public void setRecordTimeMs(long recordTimeMs) { this.recordTimeMs = recordTimeMs; }

    public UUID getHolderPlayerId() { return holderPlayerId; }

    public void setHolderPlayerId(UUID holderPlayerId) { this.holderPlayerId = holderPlayerId; }

    public LocalDateTime getAchievedAt() { return achievedAt; }

    public void setAchievedAt(LocalDateTime achievedAt) { this.achievedAt = achievedAt; }
}

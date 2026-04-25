-- =============================================================================
-- V4__map_records.sql
-- =============================================================================
-- Stores the all-time fastest completion time per (cup, map) combination.
-- The record is loaded at race start as the bracket reference time and updated
-- in-session whenever a player beats it. The first player to finish a map
-- establishes the initial record.
--
-- holder_player_id references elytra_players.playerId by convention but carries
-- no FK to avoid cascade complexity when player rows are pruned.
-- =============================================================================

CREATE TABLE IF NOT EXISTS map_records (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    cup_name         VARCHAR(255) NOT NULL,
    map_name         VARCHAR(255) NOT NULL,
    record_time_ms   BIGINT       NOT NULL COMMENT 'Fastest finish time in milliseconds',
    holder_player_id uuid         NOT NULL COMMENT 'UUID of the record holder',
    achieved_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_map_records_cup_map (cup_name, map_name),
    INDEX idx_map_records_lookup (cup_name, map_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

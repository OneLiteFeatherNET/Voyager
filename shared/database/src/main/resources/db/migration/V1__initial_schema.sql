-- =============================================================================
-- V1__initial_schema.sql
-- =============================================================================
-- Baseline of the Voyager persistence schema as of release 1.7.0.
--
-- This script reproduces the schema that Hibernate's hbm2ddl.auto=update
-- produced up to and including PR #176 (game_mode + medal_tier columns on
-- game_results). It is the Flyway baseline per ADR-0011.
--
-- All later DDL changes ship as additional V{n}__*.sql migrations. Hibernate
-- runs in validate mode in production; it never alters this schema again.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- elytra_players
--   One row per player UUID. Tracks aggregate lifetime stats and the most
--   recent display name observed at login.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS elytra_players (
    playerId            BINARY(16)      NOT NULL,
    lastKnownName       VARCHAR(255)    NULL,
    totalGamesPlayed    INT             NOT NULL DEFAULT 0,
    totalWins           INT             NOT NULL DEFAULT 0,
    totalRingsPassed    INT             NOT NULL DEFAULT 0,
    lastPlayed          DATETIME(6)     NULL,
    PRIMARY KEY (playerId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- game_results
--   One row per player per finished map. Carries the score breakdown,
--   placement, and the discriminator columns added in PR #176
--   (game_mode = RACE/PRACTICE, medal_tier = NULL for races).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS game_results (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    player_id       BINARY(16)      NOT NULL,
    cupName         VARCHAR(255)    NOT NULL,
    mapName         VARCHAR(255)    NOT NULL,
    ringPoints      INT             NOT NULL,
    positionBonus   INT             NOT NULL,
    totalPoints     INT             NOT NULL,
    placement       INT             NOT NULL,
    playedAt        DATETIME(6)     NOT NULL,
    game_mode       VARCHAR(16)     NOT NULL DEFAULT 'RACE',
    medal_tier      VARCHAR(16)     NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_game_results_player
        FOREIGN KEY (player_id) REFERENCES elytra_players (playerId),
    INDEX idx_game_results_player_mode_played (player_id, game_mode, playedAt DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

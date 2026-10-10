-- =============================================================================
-- V3__fix_uuid_column_types.sql
-- =============================================================================
-- Hibernate ORM 7 maps java.util.UUID to MariaDB's native `uuid` type.
-- The V1 baseline created both UUID columns as BINARY(16), causing Hibernate's
-- hbm2ddl=update to attempt an ALTER that MariaDB rejects because the FK
-- fk_game_results_player references elytra_players.playerId.
--
-- Fix: drop the FK, convert both columns, re-add the FK.
-- Safe to run on fresh databases too: MODIFY COLUMN is a no-op when the
-- column type is already `uuid NOT NULL`.
-- =============================================================================

ALTER TABLE game_results DROP FOREIGN KEY fk_game_results_player;

ALTER TABLE elytra_players MODIFY COLUMN playerId uuid NOT NULL;
ALTER TABLE game_results  MODIFY COLUMN player_id uuid NOT NULL;

ALTER TABLE game_results ADD CONSTRAINT fk_game_results_player
    FOREIGN KEY (player_id) REFERENCES elytra_players (playerId);

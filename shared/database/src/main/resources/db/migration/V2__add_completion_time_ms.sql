-- =============================================================================
-- V2__add_completion_time_ms.sql
-- =============================================================================
-- Epic #168 — Race Mode scoring needs the wall-clock map completion time so
-- ties are broken by speed and personal-best logic has a stable input.
--
-- NULL = DNF (player never crossed the finish line).
--
-- The composite index supports the hot lookup pattern for personal bests and
-- per-mode leaderboards: (player_id, game_mode, completion_time_ms).
-- =============================================================================

ALTER TABLE game_results
    ADD COLUMN completion_time_ms BIGINT NULL COMMENT 'Map completion time in milliseconds, NULL = DNF';

CREATE INDEX idx_game_results_completion_time
    ON game_results (player_id, game_mode, completion_time_ms);

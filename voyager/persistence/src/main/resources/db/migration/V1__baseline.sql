-- Statistics schema, version 1 (openspec change player-stats, design D4).
-- Schema voyager is created by the migrator (createSchemas); every table below lives in it.
-- Durations are integer milliseconds; totals are bigint; the counts are non-negative by check constraint.

CREATE TABLE voyager.player (
    player_id        uuid         NOT NULL,
    last_known_name  varchar(16)  NOT NULL,
    first_seen       timestamptz  NOT NULL,
    last_seen        timestamptz  NOT NULL,
    CONSTRAINT player_pkey PRIMARY KEY (player_id)
);

CREATE TABLE voyager.player_statistics (
    player_id      uuid    NOT NULL,
    cups_played    bigint  NOT NULL DEFAULT 0,
    cups_won       bigint  NOT NULL DEFAULT 0,
    races_played   bigint  NOT NULL DEFAULT 0,
    races_won      bigint  NOT NULL DEFAULT 0,
    rings_passed   bigint  NOT NULL DEFAULT 0,
    boosts_used    bigint  NOT NULL DEFAULT 0,
    resets         bigint  NOT NULL DEFAULT 0,
    flight_millis  bigint  NOT NULL DEFAULT 0,
    CONSTRAINT player_statistics_pkey PRIMARY KEY (player_id),
    CONSTRAINT player_statistics_player_fkey FOREIGN KEY (player_id) REFERENCES voyager.player (player_id),
    CONSTRAINT player_statistics_cups_played_check CHECK (cups_played >= 0),
    CONSTRAINT player_statistics_cups_won_check CHECK (cups_won >= 0),
    CONSTRAINT player_statistics_races_played_check CHECK (races_played >= 0),
    CONSTRAINT player_statistics_races_won_check CHECK (races_won >= 0),
    CONSTRAINT player_statistics_rings_passed_check CHECK (rings_passed >= 0),
    CONSTRAINT player_statistics_boosts_used_check CHECK (boosts_used >= 0),
    CONSTRAINT player_statistics_resets_check CHECK (resets >= 0),
    CONSTRAINT player_statistics_flight_millis_check CHECK (flight_millis >= 0)
);

-- The cup is referenced by race_session, so it is created first.
CREATE TABLE voyager.cup_session (
    cup_session_id  bigint       GENERATED ALWAYS AS IDENTITY,
    cup_key         text         NOT NULL,
    game_mode       varchar(16)  NOT NULL,
    started_at      timestamptz  NOT NULL,
    finished_at     timestamptz  NOT NULL,
    racer_count     integer      NOT NULL,
    CONSTRAINT cup_session_pkey PRIMARY KEY (cup_session_id),
    CONSTRAINT cup_session_game_mode_check CHECK (game_mode IN ('RACE', 'PRACTICE')),
    CONSTRAINT cup_session_racer_count_check CHECK (racer_count >= 0)
);

CREATE TABLE voyager.race_session (
    race_session_id  bigint       GENERATED ALWAYS AS IDENTITY,
    map_key          text         NOT NULL,
    layout_version   char(16)     NOT NULL,
    game_mode        varchar(16)  NOT NULL,
    cup_session_id   bigint,
    started_at       timestamptz  NOT NULL,
    finished_at      timestamptz  NOT NULL,
    human_count      integer      NOT NULL,
    bot_count        integer      NOT NULL,
    CONSTRAINT race_session_pkey PRIMARY KEY (race_session_id),
    CONSTRAINT race_session_cup_session_fkey FOREIGN KEY (cup_session_id) REFERENCES voyager.cup_session (cup_session_id),
    CONSTRAINT race_session_game_mode_check CHECK (game_mode IN ('RACE', 'PRACTICE')),
    CONSTRAINT race_session_human_count_check CHECK (human_count >= 0),
    CONSTRAINT race_session_bot_count_check CHECK (bot_count >= 0)
);

CREATE INDEX race_session_started_at_idx ON voyager.race_session (started_at);

CREATE TABLE voyager.race_result (
    race_result_id       bigint       GENERATED ALWAYS AS IDENTITY,
    race_session_id      bigint       NOT NULL,
    player_id            uuid         NOT NULL,
    finish_position      smallint,
    finish_time_millis   integer,
    ring_points          integer      NOT NULL,
    placement_bonus      integer      NOT NULL,
    medal_points         integer      NOT NULL,
    total_points         integer      NOT NULL,
    rings_passed         integer      NOT NULL,
    boosts_used          integer      NOT NULL,
    resets               integer      NOT NULL,
    flight_millis        integer      NOT NULL,
    medal_tier           varchar(16)  NOT NULL,
    validation_state     varchar(16)  NOT NULL,
    plausibility_error   double precision,
    CONSTRAINT race_result_pkey PRIMARY KEY (race_result_id),
    CONSTRAINT race_result_session_player_key UNIQUE (race_session_id, player_id),
    CONSTRAINT race_result_race_session_fkey FOREIGN KEY (race_session_id) REFERENCES voyager.race_session (race_session_id),
    CONSTRAINT race_result_player_fkey FOREIGN KEY (player_id) REFERENCES voyager.player (player_id),
    CONSTRAINT race_result_finish_position_check CHECK (finish_position IS NULL OR finish_position >= 1),
    CONSTRAINT race_result_finish_time_check CHECK (finish_time_millis IS NULL OR finish_time_millis >= 0),
    CONSTRAINT race_result_finish_pair_check CHECK ((finish_position IS NULL) = (finish_time_millis IS NULL)),
    CONSTRAINT race_result_ring_points_check CHECK (ring_points >= 0),
    CONSTRAINT race_result_placement_bonus_check CHECK (placement_bonus >= 0),
    CONSTRAINT race_result_medal_points_check CHECK (medal_points >= 0),
    CONSTRAINT race_result_total_points_check CHECK (total_points >= 0),
    CONSTRAINT race_result_rings_passed_check CHECK (rings_passed >= 0),
    CONSTRAINT race_result_boosts_used_check CHECK (boosts_used >= 0),
    CONSTRAINT race_result_resets_check CHECK (resets >= 0),
    CONSTRAINT race_result_flight_millis_check CHECK (flight_millis >= 0),
    CONSTRAINT race_result_medal_tier_check CHECK (medal_tier IN ('DIAMOND', 'GOLD', 'SILVER', 'BRONZE', 'FINISH', 'DNF')),
    CONSTRAINT race_result_validation_state_check CHECK (validation_state = 'VALID')
);

CREATE INDEX race_result_player_id_idx ON voyager.race_result (player_id);

CREATE TABLE voyager.race_split (
    race_result_id  bigint   NOT NULL,
    ring_index      integer  NOT NULL,
    game_tick       integer  NOT NULL,
    CONSTRAINT race_split_pkey PRIMARY KEY (race_result_id, ring_index),
    CONSTRAINT race_split_race_result_fkey FOREIGN KEY (race_result_id) REFERENCES voyager.race_result (race_result_id),
    CONSTRAINT race_split_ring_index_check CHECK (ring_index >= 0),
    CONSTRAINT race_split_game_tick_check CHECK (game_tick >= 0)
);

CREATE TABLE voyager.cup_result (
    cup_result_id       bigint    GENERATED ALWAYS AS IDENTITY,
    cup_session_id      bigint    NOT NULL,
    player_id           uuid      NOT NULL,
    final_position      smallint  NOT NULL,
    total_points        integer   NOT NULL,
    maps_finished       integer   NOT NULL,
    best_time_millis    integer,
    medals_diamond      integer   NOT NULL DEFAULT 0,
    medals_gold         integer   NOT NULL DEFAULT 0,
    medals_silver       integer   NOT NULL DEFAULT 0,
    medals_bronze       integer   NOT NULL DEFAULT 0,
    CONSTRAINT cup_result_pkey PRIMARY KEY (cup_result_id),
    CONSTRAINT cup_result_session_player_key UNIQUE (cup_session_id, player_id),
    CONSTRAINT cup_result_cup_session_fkey FOREIGN KEY (cup_session_id) REFERENCES voyager.cup_session (cup_session_id),
    CONSTRAINT cup_result_player_fkey FOREIGN KEY (player_id) REFERENCES voyager.player (player_id),
    CONSTRAINT cup_result_final_position_check CHECK (final_position >= 1),
    CONSTRAINT cup_result_total_points_check CHECK (total_points >= 0),
    CONSTRAINT cup_result_maps_finished_check CHECK (maps_finished >= 0),
    CONSTRAINT cup_result_best_time_check CHECK (best_time_millis IS NULL OR best_time_millis >= 0),
    CONSTRAINT cup_result_medals_diamond_check CHECK (medals_diamond >= 0),
    CONSTRAINT cup_result_medals_gold_check CHECK (medals_gold >= 0),
    CONSTRAINT cup_result_medals_silver_check CHECK (medals_silver >= 0),
    CONSTRAINT cup_result_medals_bronze_check CHECK (medals_bronze >= 0)
);

CREATE INDEX cup_result_player_id_idx ON voyager.cup_result (player_id);

-- One row per player and map key, layout version and mode. The upsert of design D4 keeps the fastest time.
CREATE TABLE voyager.map_personal_best (
    map_personal_best_id  bigint       GENERATED ALWAYS AS IDENTITY,
    map_key               text         NOT NULL,
    layout_version        char(16)     NOT NULL,
    game_mode             varchar(16)  NOT NULL,
    player_id             uuid         NOT NULL,
    best_time_millis      integer      NOT NULL,
    race_result_id        bigint       NOT NULL,
    achieved_at           timestamptz  NOT NULL,
    CONSTRAINT map_personal_best_pkey PRIMARY KEY (map_personal_best_id),
    CONSTRAINT map_personal_best_key UNIQUE (map_key, layout_version, game_mode, player_id),
    CONSTRAINT map_personal_best_player_fkey FOREIGN KEY (player_id) REFERENCES voyager.player (player_id),
    CONSTRAINT map_personal_best_race_result_fkey FOREIGN KEY (race_result_id) REFERENCES voyager.race_result (race_result_id),
    CONSTRAINT map_personal_best_game_mode_check CHECK (game_mode IN ('RACE', 'PRACTICE')),
    CONSTRAINT map_personal_best_best_time_check CHECK (best_time_millis >= 0)
);

-- Drives the leaderboard: the top N of one map, one layout and one mode, fastest first, with no sort node.
CREATE INDEX map_personal_best_leaderboard_idx
    ON voyager.map_personal_best (map_key, layout_version, game_mode, best_time_millis, achieved_at);

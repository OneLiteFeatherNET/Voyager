# ADR-0037: Store player statistics in PostgreSQL, through a non-blocking write path

## Status

Proposed

This record stays Proposed. The project owner decided on 2026-10-13 not to accept it in the change that implements it, and
the change `player-stats` does not accept it. The change implements it and depends on `adopt-avaje-config` (ADR-0036), which
is merged.

## Date

2026-10-12

## Decision makers

- TheMeinerLP (project owner). On 2026-10-12 decided to start collecting statistics now, with Hibernate ORM 7 on PostgreSQL,
  and to keep the catalogues (maps and cups) as files in git.
- Drafted with the change `player-stats`.

## Context and problem statement

The greenfield design (`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`, section "Persistence") chose
Hibernate ORM 7 with HikariCP over MariaDB, with Flyway owning the schema. The legacy tree uses MariaDB too. The owner now wants
statistics collected from now on: cup results and medals, per-run details (rings, boosts, resets, split times), per-map best
times with leaderboards, and a player profile.

Two questions follow. Which database holds the statistics of the rebuild? And how do race results reach it without
affecting the race, given that the game loop runs at 20 ticks per second?

## Decision drivers

- Data collected from now on cannot be recovered later, so collection must start with this change.
- The race and the tick must never wait for the database, and a database outage must not stop a race.
- Writes of one race or cup are atomic, and personal bests never lose an update.
- The schema needs ranges, time zones and upserts done in one statement.
- The rebuild stays separate from the legacy tree, which is deleted at cut-over (design D11, no data import).

## Considered options

1. **MariaDB, as the design says.** Already in the legacy tree and the design. Its upsert is `ON DUPLICATE KEY UPDATE` with
   `IF(...)`, and its timestamp and unsigned types need care (design, "Entity model").
2. **PostgreSQL 17 (chosen).** Native `ON CONFLICT ... DO UPDATE` with `LEAST` and `CASE`, `timestamptz`, check constraints in
   place of unsigned types, and the image and the driver that Titan already runs in production.
3. **A flat file or an embedded store (SQLite).** No concurrent operators, no shared view for leaderboards across servers, and
   a second persistence technology to test.
4. **Jakarta Data repositories.** Rejected in the design: no asynchronous return type and an annotation processor on the
   critical path. This ADR does not change that.

## Decision outcome

Chosen option: **2**, with these rules.

- The rebuild writes statistics to PostgreSQL 17 in schema `voyager`. Hibernate 7 with `StatelessSession`, HikariCP as a direct
  pool shared with Flyway, and Flyway owning the schema (`hbm2ddl.auto=validate`). No association is mapped (design, "The
  governing decision").
- Every write goes through one bounded queue and one writer thread. `RaceRecorder.recordRace` is a non-blocking offer. A full
  queue drops the newest completion with a warning; a lost database is retried with a backoff and never stops the game.
- One finished map or cup is one transaction.
- Computer racers are not stored (proposal `com-racers`, "no stored record").
- The database is `off` or `on` (`VOYAGER_PERSISTENCE`; unset means `off`). A database that is unavailable at boot never stops
  the start: the game runs without statistics, buffers completions and retries in the background, with one WARN at boot.
- The legacy tree keeps MariaDB and is not changed by this record.

**Consequences.**
- The design's "Persistence" section changes to PostgreSQL. Design decision D15 records it (task 9.5).
- The design's `cup` and `map` registry tables are dropped in favour of text keys (owner decision Q6, 2026-10-13).
- Operators provision a PostgreSQL database and set `VOYAGER_PERSISTENCE=on` in the CloudNet task to collect statistics; unset
  keeps them off. The PostgreSQL host and role are not known yet (owner question Q2); this record does not depend on them.
- The rebuild gets its first Testcontainers suite; CI runs it in one Linux job only.

## Pros and cons of the options

### Option 1, MariaDB
- Good, because the design, the legacy tree and the operator's habits already use it.
- Bad, because the design's upsert and types are MariaDB-specific, and the rebuild would carry the two in one schema.

### Option 2, PostgreSQL
- Good, because the upsert of a personal best is one statement with no read-modify-write, and `timestamptz` and checks are native.
- Good, because the organisation's Titan project runs the same driver, image and migration tooling.
- Bad, because it is a second database technology beside the legacy MariaDB until cut-over.

### Option 3, SQLite
- Good, because no server is needed.
- Bad, because a leaderboard across servers and an operator's own queries are not possible.

### Option 4, Jakarta Data
- Good, because it writes less code.
- Bad, because the design rejects it for the tick-safety reasons above.

## More information

- Design sections that still apply unchanged: "The governing decision", "Repositories" (ports not sealed), "Tick safety", "Query
  design", "Flyway", "HikariCP" (apart from the database types).
- Titan's jump-and-run records: `features/jumprun` and `persistence` in the Titan repository (PostgreSQL, Flyway, HikariCP, `TITAN_DATABASE_*`).
- Owner decisions of 2026-10-12 and 2026-10-13 are recorded in `openspec/changes/player-stats/design.md`.

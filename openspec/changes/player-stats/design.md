# Design

## Context

The rebuild has no persistence yet. The greenfield design (`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`,
section "Persistence") describes `voyager-persistence` with Hibernate 7 over MariaDB, `StatelessSession`, no associations, a
single Flyway baseline, a single writer and `void` submits. This change keeps that design and makes three changes to it, all
owner decisions of 2026-10-12: PostgreSQL instead of MariaDB, avaje-config for the keys (`adopt-avaje-config`), and a wider set
of recorded facts (splits, boosts, resets, flight time, cup medals).

The race domain already produces most of the values. Verified in the code:
- `RaceRun.passedOnGameTick()` gives the movement tick of every ring passed, in order (the split times), and `finishedAt()`.
- `RingProgress.passedCount()` gives rings passed.
- `MapScorer.score(...)` gives `MapScore` (ring points, medal points, placement bonus, medal tier through `MedalBrackets`).
- `CupRound.recordMap`, `closeMap` and `order()` give the cup's standings per map.
- `CupSession.mapFinished(...)` runs once per map for every racer with a held run, finished or not.
- `FireworkBoostTracker` accepts boosts; `RunResetter.reset(...)` performs resets. Neither counts anything today.
- Gliding is known per tick (`RaceRun.gliding`, `FlightTick`), so flight time is a count of gliding ticks.

## Goals / Non-Goals

**Goals**
- Start recording now, without changing a single race outcome or tick budget.
- A database outage never stops a race, never blocks the tick, and is visible in the log.
- Per-map leaderboards, a player profile, cup results and medals, per-run details.
- Every write of one finished race or cup is one transaction; no lost updates.

**Non-Goals**
- Importing the MariaDB data of the old tree (decision D11 of the greenfield design).
- Storing computer racers.
- Web or Discord views, cross-server statistics, data export, erasure tooling.
- Re-reading catalogues from the database. Maps and cups stay as files in git.

## Decisions

### D1. PostgreSQL for statistics (ADR-0037), versions that pass the cooling window

**Decision.** The rebuild stores statistics in PostgreSQL 17 (the image Titan's integration tests use). The legacy tree stays on
MariaDB. Versions are chosen so that each has been on Maven Central for at least two weeks on 2026-10-12 (upload dates checked):

| Artifact | Version | Uploaded | Use |
|---|---|---|---|
| `org.hibernate.orm:hibernate-core` | 7.3.11.Final (already in the catalogue) | before this change | ORM, `StatelessSession` |
| `com.zaxxer:HikariCP` | 7.1.0 | 2026-06-14 | pool (direct `HikariDataSource`, as Titan does, so Flyway and the session factory share one pool) |
| `org.flywaydb:flyway-core` | 12.10.0 (already in the catalogue) | before this change | migrations |
| `org.flywaydb:flyway-database-postgresql` | 12.10.0 | 2026-06-30 | Flyway's PostgreSQL support (required since Flyway 10) |
| `org.postgresql:postgresql` | 42.7.13 | 2026-07-06 | JDBC driver (42.7.14 uploaded 2026-10-07, inside the window: not used) |
| `org.testcontainers:testcontainers-postgresql` and `testcontainers-junit-jupiter` | 2.0.5 | 2026-04-20 | integration tests |

Flyway 13.10.0 (uploaded 2026-10-08) is inside the window and is not used. Hibernate 7.4.11.Final (2026-09-27) would pass, but it is
not needed: 7.3.11 is what the catalogue already pins. The bump can be a separate change.

### D2. Module placement and dependency direction

```
voyager-api          StatsRecorder/StatsReader ports, completion records, DatabaseConfig, Secret   (no implementation)
voyager-persistence  -> api          Hibernate, HikariCP, Flyway, PostgreSQL driver   (implements both ports)
voyager-platform     -> api          observation (platform.stats), no persistence type
voyager-server       -> all          wires PostgresStatsStore into the cup, the command and the lifecycle
voyager-fitness      -> all          ArchUnit rules below
```

Nothing points back. `voyager-race` gets no dependency at all: the race domain returns values, and the platform decides what to
record. `voyager-persistence` does not depend on `race` or `platform`; the records it stores are API records.

**Ports** (in `net.elytrarace.voyager.api.stats`, not sealed, per the design's "Repositories" section):

```java
public interface RaceRecorder {                 // written by platform.cup, never throws to the caller
    void recordRace(RaceCompletion completion); // non-blocking: an offer to a bounded queue
    void recordCup(CupCompletion completion);
}

public interface StatsReader {                  // called on a read executor, never on the tick
    PlayerProfile profile(UUID playerId);       // fails with StatsUnavailableException (a RuntimeException, rule 9)
    List<LeaderboardEntry> topTimes(String mapKey, String layoutVersion, GameMode mode, int limit);
}
```

`RaceCompletion` holds `mapKey`, `layoutVersion`, `GameMode`, the optional cup key, start and finish instants, `humanCount`,
`botCount` (informative only) and a list of `RacerRun` (player id, name, finish position, finish time, rings passed, boosts,
resets, flight millis, split ticks, `MapScore` values, `MedalTier`). `CupCompletion` holds the cup key, the mode, instants and a
list of `CupRacerResult` (player id, name, final position, total points, maps finished, best time, medal counts per tier).
They are records with compact constructors that check their invariants (non-negative counts, finish time present only when the
finish position is).

### D3. Observation points (platform, no new race logic)

| Fact | Where it comes from | Added in this change |
|---|---|---|
| Rings passed, split ticks, finish time | `RaceRun` (`passedOnGameTick`, `finishedAt`, `progress`) | no |
| Ring points, placement bonus, medal | `MapScorer` / `MapScore` | no |
| Boosts | `FireworkBoostTracker` accepts a boost | a per-racer counter, cleared with `forget` at map end |
| Resets | `RunResetter.reset` | a per-racer counter, cleared at map start |
| Flight millis | gliding flag of each `FlightTick` | a per-racer counter of gliding ticks, times 50 ms |
| Map finished, one `RaceCompletion` | `CupSession.mapFinished` | the call to `RaceRecorder.recordRace` |
| Cup finished, one `CupCompletion` | `CupSession`, where `cupFinished()` becomes true (line ~451; task 2.1 pins it) | the call to `RaceRecorder.recordCup` |

The three counters live in `platform.stats.RacerTally`, a plain class with one instance per cup, so tests need no Minestom. The
mapping from race values to `RaceCompletion` is a pure static function in `platform.stats.RaceCompletions`, unit-tested with
hand-built `RaceRun`s.

### D4. Schema outline (PostgreSQL, schema `voyager`, one migration `V1__baseline.sql`)

| Table | Key | Columns (selection) | Written when |
|---|---|---|---|
| `player` | `player_id uuid` | `last_known_name varchar(16)`, `first_seen`, `last_seen` (`timestamptz`) | every participant, upsert |
| `player_statistics` | `player_id` (1:1 with `player`) | `cups_played`, `cups_won`, `races_played`, `races_won`, `rings_passed`, `boosts_used`, `resets`, `flight_millis bigint`, all `>= 0` | every ranked participant, atomic increments |
| `race_session` | `race_session_id bigint identity` | `map_key text`, `layout_version char(16)`, `game_mode`, `cup_session_id` (nullable FK), `started_at`, `finished_at`, `human_count`, `bot_count` | once per map |
| `race_result` | `race_result_id bigint identity`, unique `(race_session_id, player_id)` | `finish_position smallint null`, `finish_time_millis int null`, `ring_points`, `placement_bonus`, `medal_points`, `total_points`, `rings_passed`, `boosts_used`, `resets`, `flight_millis`, `medal_tier` (check: `DIAMOND, GOLD, SILVER, BRONZE, FINISH, DNF`), `validation_state` (check: `VALID` in v1), `plausibility_error double null` | once per racer per map |
| `race_split` | `(race_result_id, ring_index)` | `game_tick int` | once per ring passed |
| `cup_session` | `cup_session_id bigint identity` | `cup_key text`, `game_mode`, `started_at`, `finished_at`, `racer_count` | once per cup |
| `cup_result` | `cup_result_id`, unique `(cup_session_id, player_id)` | `final_position`, `total_points`, `maps_finished`, `best_time_millis null`, `medals_diamond`, `medals_gold`, `medals_silver`, `medals_bronze` | once per racer per cup |
| `map_personal_best` | `map_personal_best_id`, unique `(map_key, layout_version, game_mode, player_id)` | `best_time_millis int`, `race_result_id`, `achieved_at` | once per finisher per map (upsert) |

Indexes: `map_personal_best (map_key, layout_version, game_mode, best_time_millis, achieved_at)` drives the leaderboard;
`race_result (player_id)` and `race_session (started_at)` serve "my last races"; `cup_result (player_id)` serves the profile.

**Deviations from the greenfield design's "Entity model"**, taken on purpose:
- No `cup` and `map` registry tables. Keys are text (`map_key` is the map's name in its file). The registry was a size
  optimisation and a rename shield; with fifty players per race it buys nothing measurable, and it would need a boot-time upsert
  from the catalogue. Owner decision Q6 (2026-10-13) keeps this: text keys, no registry tables.
- `layout_version` is the first 16 hex characters of SHA-256 over the canonical form of the map definition (see D7).
- Durations are `integer` milliseconds (cap 24.8 days, far above any race length) and `bigint` for totals. PostgreSQL has no
  `UNSIGNED`, so the check constraints `>= 0` replace it.
- Timestamps are `timestamptz`, written from an injected `Clock` (design rule 7).

Profile reads are three queries in one read-only transaction: identity plus statistics; the medal count per tier (a count of the
player's ranked `race_result` rows grouped by `medal_tier`, DIAMOND to BRONZE); and the personal bests of the player. These are
the rows that `/stats` shows (owner decision Q4, 2026-10-13).

**Upserts (atomic, no read-modify-write):**

```sql
INSERT INTO voyager.map_personal_best
       (map_key, layout_version, game_mode, player_id, best_time_millis, race_result_id, achieved_at)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT (map_key, layout_version, game_mode, player_id) DO UPDATE SET
    best_time_millis = LEAST(voyager.map_personal_best.best_time_millis, EXCLUDED.best_time_millis),
    race_result_id   = CASE WHEN EXCLUDED.best_time_millis < voyager.map_personal_best.best_time_millis
                            THEN EXCLUDED.race_result_id ELSE voyager.map_personal_best.race_result_id END,
    achieved_at      = CASE WHEN EXCLUDED.best_time_millis < voyager.map_personal_best.best_time_millis
                            THEN EXCLUDED.achieved_at ELSE voyager.map_personal_best.achieved_at END;
```

Leaderboard (top N):

```sql
SELECT pb.player_id, p.last_known_name, pb.best_time_millis, pb.achieved_at
FROM voyager.map_personal_best pb
JOIN voyager.player p ON p.player_id = pb.player_id
WHERE pb.map_key = ? AND pb.layout_version = ? AND pb.game_mode = ?
ORDER BY pb.best_time_millis ASC, pb.achieved_at ASC
LIMIT ?;
```

The integration suite asserts with `EXPLAIN (FORMAT JSON)` that this plan has no `Sort` node and uses the leaderboard index.

### D5. Write path, threading and DB-down behaviour

**Path.** `RaceRecorder.recordRace` builds nothing and touches nothing: it calls `offer` on a bounded `ArrayBlockingQueue`
(capacity `VOYAGER_DB_QUEUE_CAPACITY`, default 256 completions; one completion of fifty racers is a few kilobytes). The tick thread
never waits. One platform thread named `voyager-db-write` takes completions in FIFO order and writes each in one transaction. Reads
use `voyager-db-read`, a fixed pool of 2. Neither executor is `ForkJoinPool.commonPool()`.

**Replies from reads.** A read completes on `voyager-db-read`. The result is offered to a bounded queue that the tick drains at a
fixed point with a budget, and the chat message is sent from the tick (design, "Completions re-enter through the tick"). A read that
takes longer than two seconds is answered with "statistics are unavailable right now".

**DB-down behaviour (owner decisions Q1 and Q3, 2026-10-13; the game never refuses to start because of the database):**

| State | Behaviour |
|---|---|
| `VOYAGER_PERSISTENCE` unset or `off` | a no-op recorder is bound; one WARN at boot says no statistics are kept; `/stats` answers that statistics are not recorded on this server |
| `on`, database unreachable, migration or schema validation fails at boot | the game runs without stats; completions are buffered and the writer retries in the background with a backoff of 1 s doubling to 60 s; one WARN at boot names the failing setting, never the password |
| `on`, database lost while running | completions queue up (up to the capacity of 256); the writer retries with the same backoff; one WARN when the outage starts and one when it ends; the game is unaffected |
| queue full | the newest completion is dropped; one WARN per drop with the cup and map keys; a counter is exposed for the metrics of design E4 |
| a data error (SQLSTATE class 22 or 23) | the completion is dropped after one attempt with an ERROR that names the keys; the writer goes on |
| shutdown | the writer drains for at most 10 s; the number still queued is logged; the pool closes after the writer |

**Why buffer, not only drop.** A race that finishes during a short outage is worth keeping; the cost is bounded memory and
the loss on a crash, which the owner accepts for v1 (a spool file is a later change). Owner decision Q3 (2026-10-13): buffer
256 completions in memory, retry with a backoff of 1 s doubling to 60 s, and when the queue is full drop the NEWEST completion
with a WARN.

### D6. Computer racers are not stored

**Decision.** `RaceCompletion` carries only human racers in `RacerRun`. `bot_count` on `race_session` records how many computer racers
ran, as context only. This follows the proposal `com-racers` ("no stored record"), and it avoids three problems: a computer racer has no
player identity to reference, storing it would count it in `cups_played` and in placements, and its flight is not human flight, so it
would distort the plausibility percentiles the design plans to read from these rows. The data is not lost for good: a later table with
a racer kind can be added without a migration of these tables.

### D7. Layout version and practice runs

- `layout_version` is the first 16 hex characters of SHA-256 over the map definition's canonical JSON (the parsed definition written
  back in the schema's canonical field order, so whitespace and key order do not change it). A change of a ring or a spawn changes it,
  and the leaderboard shows only the current layout. Old rows stay and stop appearing (design, "Records are scoped by `layout_version`").
  Owner decision Q6 (2026-10-13) accepts the canonical form as the boundary.
- Every mode is stored (`RACE` and `PRACTICE`). Profile statistics and leaderboards count only modes where `GameMode.ranked()` is true,
  so practice runs are recorded for later analysis without changing a player's numbers (owner decision Q7, 2026-10-13).

### D8. Configuration (through `adopt-avaje-config`)

| Key (system property) | Environment name | Default | Rule |
|---|---|---|---|
| `voyager.persistence` | `VOYAGER_PERSISTENCE` | unset (= `off`) | `off` or `on`; unset means `off` (Q1) |
| `voyager.db.url` | `VOYAGER_DB_URL` | none | `jdbc:postgresql://host:port/database`; required when `on` |
| `voyager.db.user` | `VOYAGER_DB_USER` | none | required when `on`; no default, so a missing value is not the development user |
| `voyager.db.password` | `VOYAGER_DB_PASSWORD` | none | environment only in the documented setup; a `-D` value is accepted but the guide says not to use it |
| `voyager.db.password.file` | `VOYAGER_DB_PASSWORD_FILE` | none | a path whose content is the secret; the file is read once at boot |
| `voyager.db.pool.size` | `VOYAGER_DB_POOL_SIZE` | 10 | 2 to 20 |
| `voyager.db.queue.capacity` | `VOYAGER_DB_QUEUE_CAPACITY` | 256 | 16 to 4096 |

`Secret` (in `api.config`) wraps the password: `toString` prints `Secret[masked]`, the record has no `value()` accessor that is
public outside `persistence` and the composition root (design rule "no class outside persistence calls `Secret.value()`").
`ConfigCheck` checks the shape of the URL and the presence of user and password when `on`; it does not connect.

**Rule for invalid settings (Q1, 2026-10-13).** An unknown switch value, a missing URL, user or password while `on`, or a pool
size or queue capacity outside its range logs one WARN that names the setting and runs with stats off. None of these refuses the
start. The password is never printed, in the WARN or anywhere else.

### D9. Hibernate and pool settings

Direct `HikariDataSource` (`maximumPoolSize` from `VOYAGER_DB_POOL_SIZE`, `minimumIdle` equal to the maximum, `connectionTimeout` 5000,
`maxLifetime` 1500000, `keepaliveTime` 120000, `leakDetectionThreshold` 10000 in development and 0 in production,
`transactionIsolation` `READ_COMMITTED`, `reWriteBatchedInserts=true`, `connectionTimeZone=UTC`). The session factory is built from
`HibernatePersistenceConfiguration` with the Hikari source as non-JTA data source and `hbm2ddl.auto=validate`, as Titan does. Flyway runs
first under a PostgreSQL advisory lock, with `baselineOnMigrate=false`, `validateOnMigrate=true`, `cleanDisabled=true`, schema `voyager`.

### D10. Testing (F.I.R.S.T.)

- **Unit tests** (`persistence/src/test`, `platform/src/test`, `api/src/test`): no database, no network, no sleeps. Fakes implement the
  store. The writer's retry, queue overflow, data-error drop and drain are driven by an injected `Clock` and a `Backoff` strategy, with
  an explicit `step()` method instead of threads for the retry tests. Each test builds its own fixture.
- **Integration tests** (`persistence/src/integrationTest`, a Gradle source set, not part of `test`): Testcontainers PostgreSQL 17, one
  container per test class, one schema per test. They run `V1__baseline.sql`, validate the mappings, execute every named query, prove
  the upsert keeps the minimum under two concurrent writers (2 x 500 updates, one fixed expected value), assert the leaderboard plan with
  `EXPLAIN`, and check the DB-down rows of D5 against a container stopped mid-test.
- **Docker.** The integration base class uses `postgres:17-alpine` and is annotated `@Testcontainers(disabledWithoutDocker = true)`
  (owner decision, 2026-10-13). Without Docker it skips locally. When the Gradle property `voyager.requireDocker` is `true`, the
  base class first asserts that Docker is available, so the suite fails instead of skipping; the skip is allowed only when the
  property is unset. CI sets it with `-Pvoyager.requireDocker=true`, so a missing Docker cannot turn the integration suite
  silently green. GitHub-hosted `windows-latest` and `macos-latest` runners do not provide Linux containers by default, so the
  integration suite runs in one new job, `persistence-integration`, on `ubuntu-latest`; the existing three-OS job runs unit tests only.

### D11. Fitness rules (`voyager/fitness`, `allowEmptyShould(false)`)

- `FitnessCoverageTest`: `":voyager:persistence" -> "net.elytrarace.voyager.persistence"`; `voyager/fitness/build.gradle.kts` adds
  `testImplementation(project(":voyager:persistence"))`.
- `persistenceDependsOnlyOnApiAndTheStorageLibraries`: classes in `net.elytrarace.voyager.persistence..` do not depend on `..race..`,
  `..platform..`, `..server..`, `..setup..`, `..physics..`.
- `raceAndPlatformDoNotDependOnStorageTechnology`: `race` and `platform` do not depend on `org.hibernate..`, `jakarta.persistence..`,
  `com.zaxxer..`, `org.flywaydb..`, `org.postgresql..` (extends the existing `raceDoesNotDependOnPersistenceTechnology`).
- `entitiesHaveNoAssociations` and `entitiesAreNotLeaked`: classes in `..persistence.entity..` are `@Entity`, non-final, have no-argument
  constructors and no association annotation; no public method outside `..persistence.entity..` declares an entity type.
- `noBlockingOnFuturesOnTheTick`: no class in `race..` or `platform..` calls `CompletableFuture.get`, `join` or `Future.get`.
- `secretValueIsConfined`: `Secret.value()` is called only from `..persistence..` and `..server..`.
- `ApiPurityTest` is extended with `apiDoesNotDependOnStorage` (the API has no Hibernate or JDBC).

### D12. Observability

Counters (`writes_ok`, `writes_retried`, `writes_dropped_full`, `writes_dropped_data`, `queue_depth`) are kept in the store and written to the
log at each outage boundary. Hikari's `pendingThreads` is checked in the integration suite (it must be zero in a single-writer run). The
metrics wiring of design E4 is a later change; the counters are plain getters for now.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| A bad migration blocks boot | the integration suite runs V1 against PostgreSQL 17 on every change; a failed migration with `on` runs the game without stats and logs one WARN, so it cannot block boot |
| The queue holds results in memory during an outage | bounded (256); the loss on crash is accepted (owner decision Q3) |
| A race result is written twice | unique keys on `(race_session_id, player_id)`; the session id is created once per map |
| Upsert contention on `player_statistics` | one row per player, written once per race; the writer is single-threaded, so there is no contention inside the server |
| `/stats` answers from a stale read | reads are not cached; the answer is the database's |
| The operator forgets `VOYAGER_PERSISTENCE` | unset means `off` and logs one WARN at every boot, so the omission is visible in the log (Q1) |
| A map edit changes `layout_version` mid-season | intended (design); the leaderboard shows the new layout only |

## Migration Plan

1. No data migration. The schema is new in its own database (`voyager`), so the MariaDB legacy data is untouched and stays out (D11 of the design).
2. Provision the database and role (owner question Q2; not yet answered, and it does not block this change; the docs use placeholders
   such as `db.example.invalid`). To collect statistics, set `VOYAGER_PERSISTENCE=on` and the URL, user and password in the CloudNet
   task's environment (node-level, as the design's secrets rule says). Without it, leave the switch unset: statistics stay off. The
   guide states both.
3. Rollback: set `VOYAGER_PERSISTENCE=off`. Schema and rows stay; nothing else changes.

## Owner questions (answered)

- **Q1.** `VOYAGER_PERSISTENCE` unset: refuse to start (recommended; the design's rule, so an omission is loud) or treat as `off` with a WARN
  until the owner flips it?
  Decision: unset means `off`, with one WARN at boot that no statistics are kept; the game runs normally. `on` uses the database. If
  the database is down at boot, the game runs without stats, completions are buffered and the writer retries in the background. The
  game never refuses to start because of the database. The switch value `required` is renamed to `on`.
- **Q2.** Which PostgreSQL server holds `voyager`, who provisions the role, and is it reachable from the CloudNet nodes? Titan's configuration
  is a hint, not an answer.
  Decision: the operator session provisions the PostgreSQL host and role. It is not known yet and does not block this change; the docs
  use placeholders (for example `db.example.invalid`) and no real host is written.
- **Q3.** DB-down: buffer up to 256 completions in memory and drop the newest on overflow (recommended), or drop immediately with a WARN
  (simpler, loses every race of an outage)?
  Decision: buffer 256 completions in memory; retry with a backoff of 1 s doubling to 60 s; when the queue is full, drop the newest
  completion with a WARN.
- **Q4.** `/stats` and leaderboard UX: command names (`/stats [player]`, `/leaderboard <map>` proposed); what the profile shows (wins,
  cups played, total flight time, best time per map); own profile only, or anyone's; chat only or clickable; leaderboard length (10
  proposed).
  Decision: `/stats [player]` (no argument means the sender) and `/leaderboard <map>`. The profile shows cups played, cup wins, medals
  per tier (total per tier), total flight time and the best times (personal bests). Anyone may use both; no permission node. Replies
  are chat messages, not clickable. The player text exists in de_DE (informal "du") and en_US. The leaderboard shows the top 10
  personal bests of the current layout in ranked mode, fastest first.
- **Q5.** Retention: keep all rows (recommended for v1, the volume is small) or purge after a period; who may ask for erasure, and by
  which tool.
  Decision: keep all rows indefinitely. The retention purge and the erasure tooling are a follow-up change, out of scope here.
- **Q6.** Layout identity: is the canonical JSON of the map definition the right boundary for `layout_version` (recommended), or should
  the owner mark a layout change by hand? Also: keep the `cup` and `map` registry tables of the design, which this change drops?
  Decision: the canonical JSON of the map definition (D7) is the layout identity; the `cup` and `map` registry tables stay dropped
  (text keys).
- **Q7.** Practice runs: stored, flagged by mode, and excluded from profile and leaderboards (proposed), or not stored at all?
  Decision: practice runs are stored with their mode and excluded from profile and leaderboards (D7).
- **Q8.** Confirm the bot exclusion (D6) before `com-racers` lands.
  Decision: computer racers are never stored; only `bot_count` on the race session (D6).

## Owner decisions (2026-10-13)

Q1 to Q8 are answered above, each with its "Decision:" line. The other owner decisions of 2026-10-13 that touch this change:

- **Stack unchanged.** PostgreSQL, Hibernate 7, HikariCP and Flyway, configured through avaje-config, following the Titan pattern.
- **Switch values.** `VOYAGER_PERSISTENCE` is `off` or `on`; unset means `off`. The value `required` is not used anywhere.
- **Integration tests.** `postgres:17-alpine` with `@Testcontainers(disabledWithoutDocker = true)`; CI fails instead of skipping,
  through `voyager.requireDocker` (D10).
- **Velocity secret.** Stays environment-first, the existing exception. This change does not touch it.
- **ADR-0037.** Stays Proposed. The owner has not accepted it and will not accept it in this change.
- **Greenfield design.** The amendments D15 (PostgreSQL) and D16 (avaje-config) stay as task 9.5 and are not done here.

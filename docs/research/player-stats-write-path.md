# Research note: the write path and schema of player statistics

**Authors:** Voyager Development Team | **Date:** 2026-10-14 | **Status:** Draft. The integration suite has not yet run for this
change (task 10.4), and no timing has been measured.

## Abstract

The rebuild records every finished map and cup as player statistics in PostgreSQL 17. The race tick must never wait for the
database, and a database outage must never stop a race. This note records how the change `player-stats` meets those two
requirements: a bounded queue between the tick and one writer thread, a retry backoff, and a drop policy with a log line for
each drop. It also records the schema decisions behind the write path, the integration tests that pin them, and the
evidence that exists for each claim. The note makes no performance claim. No timing was measured, and the captured
`EXPLAIN` output is not in the repository. Five open questions remain, among them the link between a race and its cup, and
the assumption behind the instant of a personal best.

## 1. Question

Two questions drive the design:

1. Can a finished race be recorded in one transaction, with personal bests that never lose an update, without the write
   path blocking the game tick?
2. Can a database that is missing, slow or lost at any moment leave the game running, with the loss bounded and visible in the
   log?

## 2. Method

The note is written from the code and the change. It uses no measurements taken for this note.

1. **Read the design.** `openspec/changes/player-stats/design.md`, decisions D1 to D12, and the owner answers Q1 to Q8 of the
   same file.
2. **Read the code paths** that carry a completion from the tick to the database: `StatsWriter`, `StatsWriterThread`,
   `ExponentialBackoff`, `SqlStateClassifier`, `PostgresCompletionStore`, `StatsBinding` and `StatsRequests`.
3. **Read the tests.** The unit tests of `voyager/persistence/src/test` and the seven integration test classes of
   `voyager/persistence/src/integrationTest`.
4. **Check the claims against the tasks.** `openspec/changes/player-stats/tasks.md` marks each task as done. It records the order
   of Red and Green per task, but no run results. Task 10.4, the full integration run, is still open.

## 3. Schema decisions

The schema is one Flyway migration, `V1__baseline.sql`, in the schema `voyager`. Five decisions shape it (design D4).

| Decision | Consequence in the migration |
|---|---|
| Text keys for maps and cups, no registry tables | `map_key` and `cup_key` are `text`. The registry was dropped (owner decision Q6). |
| `timestamptz` for instants | Instants are stored as absolute times and do not depend on the session time zone. |
| `integer` milliseconds for durations, `bigint` for totals | A race duration fits in `integer` (about 24.8 days at most). Totals use `bigint`. |
| Check constraints in place of unsigned types | Every non-negative column carries `CHECK (... >= 0)`, and enumerated columns list their allowed values. |
| The personal best is one row per (map, layout, mode, player) | `map_personal_best` has the unique constraint `map_personal_best_key`. The leaderboard index is `map_personal_best_leaderboard_idx` on `(map_key, layout_version, game_mode, best_time_millis, achieved_at)`. |

The upsert of a personal best is one statement with `ON CONFLICT ... DO UPDATE`. It keeps the lower time with `LEAST` and moves
`race_result_id` and `achieved_at` only when the new time is lower. No read precedes the write, so two concurrent finishers
cannot lose an update through a read-modify-write gap. The old design wrote the same record in two statements, which is the lost
update the design names as its motivation.

Each race or cup is one transaction. The race transaction writes the session, the results, the splits, the personal bests and
the statistics of the racers. A failure midway leaves no row of that race visible (`PostgresCompletionStoreIT`).

The layout identity is `layout_version`, the first 16 hex characters of SHA-256 over the canonical geometry of the map. The
canonical writer is `platform.catalog.writer.MapDefinitionCanonicalWriter`, and the identity is `LayoutVersion`. A tuning value
does not change it.

## 4. Write path

The write path has four parts. The tick hands over a completion and never waits. A single thread writes. A failed write is
retried, dropped, or logged, depending on its cause.

**Hand-off.** `RaceRecorder.recordRace` and `recordCup` offer the completion to a bounded `ArrayBlockingQueue` (`StatsWriter`,
constructor parameter `capacity`). The offer returns at once, so the tick never waits for the database. The default capacity is
256 completions (`DatabaseConfig.DEFAULT_QUEUE_CAPACITY`), with an allowed range of 16 to 4096. The design estimates a completion of
fifty racers at a few kilobytes, which bounds the memory of a full queue.

**One writer.** One daemon thread named `voyager-db-write` (`StatsWriterThread.THREAD_NAME`) takes completions in FIFO order and
writes each in one transaction. It idles in 50 ms steps when the queue is empty. Reads run on a separate pool of two threads named
`voyager-db-read` (`StatsRequests.READER_THREAD_PREFIX`). The opening of the database runs on `voyager-db-open`
(`StatsBinding.OPEN_THREAD_NAME`). None of these is the common fork-join pool.

**Retry.** A failed write is retried with `ExponentialBackoff(1 s, 60 s)` (`StatsBinding.RETRY`). The delay doubles after each
failure and is capped at 60 s; the implementation caps the doubling count so that the shift cannot overflow. For a database lost while
the server runs, the design (D5) specifies one WARN when the outage starts and one when it ends.

**Drop policy.** Three causes lead to a drop, and each is counted and logged:

| Cause | Action | Evidence |
|---|---|---|
| The queue is full | The newest completion is dropped, with one WARN naming the keys. The count is kept in `droppedFull`. | `StatsWriter`, javadoc of the constructor and the `droppedFull` counter |
| A data error, SQLSTATE class 22 or 23 | The completion is dropped after one attempt, with one ERROR. The count is kept in `droppedData`. | `SqlStateClassifier`, its javadoc and `SqlStateClassifierTest` |
| Shutdown with a non-empty queue | The writer drains for at most 10 s (`StatsBinding.SHUTDOWN_DRAIN`). If it does not stop in that time, the number of completions not written is logged. | `StatsBinding`, the log line "did not stop within" |

The design (D5, D12) states that a data error is not retried, because the same completion would fail again. The classifier treats
an unknown or missing SQLSTATE as retryable, so an unexpected failure does not discard a race.

**The game is not blocked by the database.** The boot never refuses to start because of the database (owner decision Q1). With
the switch on and the database unreachable at boot, the server runs without statistics, buffers completions, and retries in the
background (`StatsBinding`, the `OPEN_THREAD_NAME` attempts). With the switch unset or `off`, a no-op recorder is bound and one
WARN states that no statistics are kept.

## 5. Tests and measurements

**Unit tests.** The writer's retry, overflow, data-error drop and drain are tested with an injected clock and a fake store
(`StatsWriterTest`, `FakeCompletionStore`, `ManualClock`). They need no database and no sleep.

**Integration tests.** Seven classes run against PostgreSQL 17 under Testcontainers. Without Docker they skip locally (`LeaderboardPlanIT` declares
`@Testcontainers(disabledWithoutDocker = true)`), and the design (D10) makes the skip a failure when `voyager.requireDocker=true`
is set, as CI does. The classes and their test methods, as found in the source, are:

| Class | Test methods | What the methods pin |
|---|---|---|
| `StatsSchemaIT` | 4 | The baseline applies once in schema `voyager`; a second start applies nothing; the mappings validate; every named query executes. |
| `PersonalBestConcurrencyIT` | 1 | Two writers, 500 upserts each, keep the fastest time (`UPSERTS_PER_WRITER = 500`). |
| `PersonalBestUpsertIT` | 1 | An equal time keeps the earlier finish. |
| `LeaderboardPlanIT` | 1 | The leaderboard plan has no sort node and reads `map_personal_best_leaderboard_idx`. |
| `PostgresStatsReaderIT` | 9 | Profile, medals, personal bests of ranked modes, name lookup, leaderboard order and limit, practice exclusion. |
| `PostgresCompletionStoreIT` | 6 | One race writes its session, results, splits, bests and statistics in one transaction; a failure midway leaves nothing; a practice race writes no best; a computer racer is never stored; a cup writes its results. |
| `DatabaseLostIT` | 2 | A completion is kept while the database is down and written after it returns; a boot against a stopped database runs without statistics and starts writing when the database returns. |

The `LeaderboardPlanIT` test seeds at least 2000 personal bests for the test map and a second map, so that the index, not the
size of the table, decides the plan. It runs `ANALYZE` and then `EXPLAIN (FORMAT JSON)`. The test asserts two things: no node of
the plan has a `Sort` type, and an `Index Scan` node names `map_personal_best_leaderboard_idx`.

**Measurements.** None. No latency, throughput or queue depth was measured for this note. The change records no timing. The
concurrency test asserts a final value; it does not record a duration. The `EXPLAIN` output of the test is not written to the
repository, so this note cannot quote the plan's nodes. The brief for this note mentioned a `Limit` node and a `Nested Loop`
node. The test does not assert them, and no captured plan confirms them, so they are not reported as findings.

**Status of the suite.** The tasks that introduced each class are closed in `tasks.md` (6.1, 6.5, 6.6, 6.8 and the tests of the
writer). The full run with `-Pvoyager.requireDocker=true` is task 10.4 and has not been executed for this note. The result of
that run belongs in the pull request, and this note should be updated with it.

## 6. Findings

1. **The schema encodes the design's decisions without a registry.** Text keys, `timestamptz`, integer milliseconds and check
   constraints replace the MariaDB types one for one. The schema has one migration and one owner, `voyager`.
2. **The upsert of a personal best is one statement.** The keep-the-minimum rule is in SQL, not in Java, so two writers cannot
   interleave a read and a write. The concurrency test pins this for two writers.
3. **The write path cannot block the tick.** The tick calls an offer on a bounded queue. The only place that waits is the writer
   thread, and the reads run on their own pool. The archunit rule that forbids waits on futures in the tick packages is covered
   in section 7, item 4.
4. **Every loss is counted and logged.** A full queue, a data error and a shutdown timeout each have a log line and a counter in
   the code. The design (D5) specifies one WARN at the start of an outage and one at its end.
5. **The fitness rules of the module are declared, not yet run for this note.** `voyager-fitness` covers the persistence module
   with the rules of design D11. Whether they pass is part of task 10.4, which has not run.

## 7. Open questions

1. **The link from a race to its cup is not written in v1.** `race_session.cup_session_id` exists in the migration and in the
   entity, but the insert of a race session does not list that column (`PostgresCompletionStore`, `INSERT_RACE_SESSION_SQL`).
   The column is therefore `NULL` for every race. A cup's standings come from `cup_result`. The question is whether v1 should
   set the link. The race completion carries the cup's key, not a row identifier, and the cup row is written at the end of the cup,
   after the races of its maps. Setting the link therefore needs a change of order or of identifiers.
2. **The `achieved_at` of a personal best assumes that the map's start equals the race clock's start.** The store computes
   `achieved_at` as the start of the race plus the finish time of the run (`PostgresCompletionStore`, where the finish instant is
   built from `startedAt` and `finishTimeMillis`). If the race clock started later than the map, the instant is shifted by that
   difference. The design describes the instant as the time of the achievement; the code derives it from the race start, so the
   two agree only while the assumption holds.
3. **The layout version covers the geometry only.** `layout_version` hashes the spawn and the rings in list order. A change of
   `boostConfig` or `ringEffects` leaves it unchanged, by design (D7, owner decision Q6). The rule is pinned by the canonical writer
   tests, but a retune of the boost of a map keeps the same leaderboard, which is the intent and should be stated in the map
   documentation when a map changes.
4. **Blocking waits are frozen debt.** The architecture rule `noBlockingOnFuturesOnTheTick` in `voyager/fitness` forbids waits on
   futures in the `race` and `platform` packages. It is a frozen rule: its violation store
   (`voyager/fitness/src/test/resources/archunit_store/`, the file for that rule) lists 11 existing violations. They are
   `Arrivals` (six entries, at lines 61, 75, 85 and 89), `MapInstances` (line 182), `InFlightChunkLoads` (lines 35, 60 and 61) and
   `LobbyHologram` (line 45). New waits fail the build; the 11 do not. Removing them is a separate change, and each one stalls the
   tick for as long as the call takes.
5. **Retention and erasure.** Records are kept indefinitely (owner decision Q5). No purge exists, and no tool erases one player's
   records. The schema allows it with one delete by `player_id` across the tables that reference the player, but the tooling is a
   follow-up change.

## 8. Limits of this note

- It records no timing. A claim about latency, throughput or the size of an outage needs a measurement that does not exist yet.
- It does not check the test results against a run. The status of the suite is the status of task 10.4.
- It treats the owner decisions of 2026-10-13 as given. They are recorded in `design.md`, section "Owner questions (answered)".

## References

- `openspec/changes/player-stats/design.md`, decisions D1 to D12
- `openspec/changes/player-stats/tasks.md`, section 6 and task 10.4
- `docs/decisions/0037-player-stats-on-postgresql.md`
- `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`, section "Persistence", as amended
- `voyager/persistence/src/main/resources/db/migration/V1__baseline.sql`
- `voyager/fitness/src/test/resources/archunit_store/` (the frozen violations of `noBlockingOnFuturesOnTheTick`)

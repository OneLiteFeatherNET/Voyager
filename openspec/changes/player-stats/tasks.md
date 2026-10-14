# Tasks

Commit type for every code group: `feat(stats)`, including the tests written with it. Documentation commits use `docs` and
artifact edits use `docs(openspec)`. Each behaviour gets a failing test first (Red), then the code (Green). Every unit test follows
F.I.R.S.T.; the integration tests may relax Fast and isolation from real collaborators, never independence or repeatability.
No `Thread.sleep`; the clock is injected; each test builds its own fixture.

Haiku effort per group: `low` for mechanical edits, `medium` for code with a test contract, `high` where a wrong choice loses data
or blocks the tick.

## 1. Decisions and prerequisites

- [ ] 1.1 ADR-0037 stays Proposed (owner decision 2026-10-13); the record is not accepted by this change (`docs`). Haiku effort: low.
- [x] 1.2 Owner answers Q1 to Q8 in design.md, "Owner questions (answered)" (answered 2026-10-13; `docs(openspec)`). Haiku effort: low.
- [x] 1.3 `adopt-avaje-config` is merged to `main` before any task of this change starts (dependency; merged, archived as `2026-10-13-adopt-avaje-config`). Haiku effort: low.
- [x] 1.4 Catalogue entries in `settings.gradle.kts`: `hikaricp` 7.1.0, `flyway-database-postgresql` 12.10.0, `postgresql` 42.7.13, `testcontainers-postgresql` and `testcontainers-junit-jupiter` 2.0.5, each with a comment naming its upload date and the cooling window. Do not change the `hibernate` or `flyway` pins (`feat(stats)`). Haiku effort: low.

## 2. Spike (no commit, results into design.md)

- [x] 2.1 Pin the observation points: the call that ends a cup (`CupSession`, the branch on `cupFinished()`), the accept point of `FireworkBoostTracker`, the success path of `RunResetter.reset`, and the gliding flag of `FlightTick` where it is sampled. Record the exact method names in design.md D3. Haiku effort: high.
- [x] 2.2 Pin the canonical form of a map definition for `layout_version` (D7): which writer exists (`MapDraftJsonWriter`, `MapDefinitionAdapter`) and whether a canonical write produces stable bytes. Record in design.md D7. Haiku effort: high.
- [ ] 2.3 Confirm PostgreSQL 17 with Flyway 12.10.0 and `flyway-database-postgresql` runs `V1` in schema `voyager` with an advisory lock on a throwaway container (run by hand, no commit). Haiku effort: medium.

## 3. api: records, ports and settings

- [x] 3.1 Red: `RaceCompletionTest` names the behaviours "a racer's counts are never negative", "a finish time is present exactly when a finish position is", "a completion lists humans only and keeps the bot count", and "a cup result's medal counts are never negative" (`feat(stats)`). Haiku effort: medium.
- [x] 3.2 Green: add `api.stats` records `RaceCompletion`, `RacerRun`, `CupCompletion`, `CupRacerResult`, `PlayerProfile`, `LeaderboardEntry`, the ports `RaceRecorder` and `StatsReader`, and `StatsUnavailableException` with its `package-info.java` (`feat(stats)`). Haiku effort: medium.
- [x] 3.3 Red: `DatabaseConfigTest` names "an on database without a URL logs one WARN, names the URL and runs with stats off", "a secret prints as masked", and "a pool size outside two to twenty logs one WARN and runs with stats off" (`feat(stats)`). Haiku effort: medium.
- [x] 3.4 Green: add `api.config.DatabaseConfig` and `api.config.Secret` (`feat(stats)`). Haiku effort: medium.

## 4. platform: observation

- [x] 4.1 Red: `RaceCompletionsTest` (pure, hand-built `RaceRun`s and `MapScore`s) names "a finished racer's splits are the ring ticks in ring order", "a racer who did not finish has no finish time and the DNF medal", "a computer racer is not in the completion", and "the medal and points come from the map score" (`feat(stats)`). Haiku effort: medium.
- [x] 4.2 Green: `platform.stats.RaceCompletions` as a pure static mapping (`feat(stats)`). Haiku effort: medium.
- [x] 4.3 Red: `RacerTallyTest` names "a boost is counted once per accepted boost", "a reset is counted per reset", "flight time is the gliding ticks times fifty milliseconds", and "the tally is cleared at the start of the next map" (`feat(stats)`). Haiku effort: medium.
- [x] 4.4 Green: `platform.stats.RacerTally`, one per cup, with the three counters; wire the accept of `FireworkBoostTracker`, the reset of `RunResetter` and the gliding ticks of the flight sampler to it, without changing their behaviour (`feat(stats)`). Haiku effort: high.
- [x] 4.5 Red: `CupSessionRecordingTest` with a fake `RaceRecorder` and explicit ticks names "a finished map records one completion per map", "a finished cup records one cup completion", "the recorder is never called on a tick that does not finish a map", and "a throwing recorder does not stop the map" (`feat(stats)`). Haiku effort: high.
- [x] 4.6 Green: `CupSession` takes a `RaceRecorder` through its constructor and calls it at `mapFinished` and at the cup end; `platform.stats.NoOpRaceRecorder` for `off` (`feat(stats)`). Haiku effort: high.
- [x] 4.8 Red then green: flight time and boosts stop at the finish. `CupSessionRecordingTest` names "glidingAfterTheFinishIsNotCounted" and "boostsAndResetsAfterTheFinishAreNotCounted"; `raceTick` counts a gliding tick only while the run is unfinished after the advance, and `requestBoost` counts a boost only for an unfinished run (`feat(stats)`). Haiku effort: high.
- [x] 4.9 Test: the completion carries the racer's boosts, resets and flight millis, with one boost, one reset and two gliding ticks before the finish. It passes on the code of 4.8, so it is a test-only commit (`feat(stats)`). Haiku effort: medium.

## 5. persistence: the write path with fakes

- [ ] 5.1 Red: `StatsWriterTest` (fakes, injected `Clock` and backoff, no threads: explicit `step()`) names "a completion is offered without waiting", "a full queue drops the newest and counts it", "a lost database is retried with backoff up to sixty seconds", "the outage start and end are logged once each", "a data error drops one completion and the next is written", "completions are written in finish order", and "drain stops after the time limit and reports the remainder" (`feat(stats)`). Haiku effort: high.
- [ ] 5.2 Green: `persistence.StatsWriter` with the bounded `ArrayBlockingQueue`, the writer loop, the retry policy and the drain; the store is a port inside the module (`feat(stats)`). Haiku effort: high.
- [ ] 5.3 Red: `SqlStateClassifierTest` names "class 22 is a data error", "class 23 is a data error", "class 08 is a connection error", and "an unknown state is retried" (`feat(stats)`). Haiku effort: low.
- [ ] 5.4 Green: `persistence.SqlStateClassifier` (`feat(stats)`). Haiku effort: low.

## 6. persistence: schema and integration

- [ ] 6.1 Red: `StatsSchemaIT` (Testcontainers PostgreSQL 17, one container per class, one schema per test) names "the baseline applies once in schema voyager", "the mappings validate against it", "every named query executes", and "a second start applies nothing" (`feat(stats)`, source set `integrationTest`). Haiku effort: high.
- [ ] 6.2 Green: `persistence/src/main/resources/db/migration/V1__baseline.sql` with the tables, checks and indexes of design.md D4, and the Flyway runner under an advisory lock with `baselineOnMigrate=false`, `validateOnMigrate=true`, `cleanDisabled=true` (`feat(stats)`). Haiku effort: high.
- [ ] 6.3 Red: `PersonalBestConcurrencyIT` names "two writers keep the fastest time" with 2 x 500 upserts and one expected value (`feat(stats)`). Haiku effort: medium.
- [ ] 6.4 Green: the upsert of design.md D4, through `StatelessSession` native mutation (`feat(stats)`). Haiku effort: high.
- [ ] 6.5 Red: `LeaderboardPlanIT` names "the leaderboard uses its index and has no sort node", checked with `EXPLAIN (FORMAT JSON)` on a seeded table of at least 2000 rows (`feat(stats)`). Haiku effort: medium.
- [ ] 6.6 Green: the leaderboard and profile queries as HQL constructor expressions or native reads returning API records; the profile is three queries in one read-only transaction (`feat(stats)`). Haiku effort: high.
- [ ] 6.7 Red: `DatabaseLostIT` stops the container mid-test and names "a completion is kept while the database is down and written after it returns", and "an on-start against a stopped database runs without stats and starts writing after the database returns" (`feat(stats)`). Haiku effort: high.
- [ ] 6.8 Green: the writer's transaction for a race (session, results, splits, personal bests, statistics) and a cup, one transaction each (`feat(stats)`). Haiku effort: high.

## 7. server: configuration, wiring and fitness

- [ ] 7.1 Red: `PersistenceSettingsTest` (lookup seam, no JVM) names "unset persistence is off and logs one WARN" (per Q1), "off needs no URL", "on without URL, user or password logs one WARN naming the setting and runs with stats off", "the password file is read and its content is the secret", and "a pool size of one logs one WARN and runs with stats off" (`feat(stats)`). Haiku effort: medium.
- [ ] 7.2 Green: the keys of design.md D8 read through the loader from `adopt-avaje-config`, and the shape checks in `ConfigCheck` (no connection); a failed check logs its WARN and runs with stats off, it never refuses the start (`feat(stats)`). Haiku effort: medium.
- [ ] 7.3 Red: `PersistenceWiringTest` on a fresh Minestom instance with a fake store names "unset and off bind the no-op recorder", "on binds the store", and "the cup of a server with persistence off records nothing" (`feat(stats)`). Haiku effort: medium.
- [ ] 7.4 Green: `server.inject.PersistenceBeans` (`@Factory`, the only DI annotation, in the composition root), wired into `CupBeans` (`feat(stats)`). Haiku effort: high.
- [ ] 7.5 Red: fitness rules of design.md D11 added to `voyager/fitness` with `allowEmptyShould(false)`; run `./gradlew :voyager:fitness:test`; record the red (`test(stats)`). Haiku effort: medium.
- [ ] 7.6 Green: add `:voyager:persistence` to `settings.gradle.kts`, to `voyager/fitness/build.gradle.kts`, to `FitnessCoverageTest`'s map; the rules pass (`feat(stats)`). Haiku effort: medium.
- [ ] 7.7 CI: a new job `persistence-integration` on `ubuntu-latest` in `.github/workflows/build.yml` runs `./gradlew :voyager:persistence:integrationTest -Pvoyager.requireDocker=true`. The existing matrix names stay unchanged (`ci`). Haiku effort: medium.

## 8. platform and server: /stats and /leaderboard

- [ ] 8.1 Red: `StatsCommandTest` with a fake `StatsReader` and explicit ticks names "a read answer is sent from the tick, not from the reader thread", "an unknown player is told there is no record", "a read over two seconds answers unavailable", and "persistence off answers not recorded" (`feat(stats)`). Haiku effort: high.
- [ ] 8.2 Green: `platform.stats.StatsRequests` (a bounded queue drained by the tick with a budget), the two commands in `server.command` with no permission node, and the messages in `voyager_en_US.properties` and `voyager_de_DE.properties` (German with informal "du") with `<arg:N>` placeholders (`feat(stats)`). Haiku effort: high.

## 9. Documentation

- [ ] 9.1 `docs/guides/how-to-deploy-on-cloudnet.md`: the database variables in the node table, the rule for `VOYAGER_PERSISTENCE` (set `on` to collect statistics, or leave it unset for `off`, in the task before the release), and the rollback (`docs`). Haiku effort: low.
- [ ] 9.2 `docs/reference/database.md` (Diataxis reference): keys, environment names, defaults, the DB-down table of design.md D5 (`docs`). Haiku effort: medium.
- [ ] 9.3 `docs/guides/run-with-postgresql-locally.md` and `docker/postgres/compose.yml` (new file; the MariaDB compose files are not touched) (`docs`). Haiku effort: low.
- [ ] 9.4 `docs/decisions/0037-player-stats-on-postgresql.md` (MADR, stays Proposed per task 1.1; owner decision 2026-10-13) (`docs`). Haiku effort: medium.
- [ ] 9.5 Amend the greenfield design spec: Persistence section (PostgreSQL types, the registry deviation of D4) and Configuration section (the keys through the loader); add rows D15 (PostgreSQL) and D16 (avaje-config) to the decision table (`docs`). Haiku effort: medium.
- [ ] 9.6 Lumen's research note in `docs/research/`: the schema decisions, the write-path measurements from the integration suite, and the open questions (`docs`). Haiku effort: medium.

## 10. Verification and pull request

- [ ] 10.1 Smoke boot, `VOYAGER_PERSISTENCE` unset: `./gradlew :voyager:server:shadowJar`, then `java -DVOYAGER_DATA_PATH=<run>/catalog -DVOYAGER_WORLDS_PATH=<run>/worlds -jar <jar>` expects exactly one WARN that no statistics are kept, then "Done", and stops on `stop` with exit 0 (mandatory). Haiku effort: medium.
- [ ] 10.2 Smoke boot, `VOYAGER_PERSISTENCE=on` against a throwaway PostgreSQL 17 container started by the operator (`docker run` of `postgres:17-alpine` under a random container name; never `docker/mariadb`): the boot succeeds and applies version 1. `/stats` and the two-human flow are not run live (no client); they are covered by the integration tests, and the report says so (mandatory). Haiku effort: high.
- [ ] 10.3 Smoke boot, `VOYAGER_PERSISTENCE=on` with an unreachable `VOYAGER_DB_URL`: the boot succeeds, and the buffered/retry WARN is logged. The mid-race outage is covered by `DatabaseLostIT`, not run live (mandatory). Haiku effort: high.
- [ ] 10.4 `./gradlew build` on both trees, `./gradlew :voyager:fitness:test`, `./gradlew :voyager:persistence:integrationTest -Pvoyager.requireDocker=true` (`docs(openspec)` for the archive note only). Haiku effort: medium.
- [ ] 10.5 `openspec validate player-stats --strict` is clean. Haiku effort: low.
- [ ] 10.6 Open the pull request. Title: `feat(stats): record race results and player statistics in postgresql`. Body: the smoke boot outputs of 10.1 to 10.3, the integration suite result, the EXPLAIN plan, and the footer with two lines: `https://claude.ai/referral/m5Ak2Sa7aQ` and `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`. Haiku effort: low.

# Proposal

**Conventional Commits:** `feat(stats)`. Squash-merge PR title: `feat(stats): record race results and player statistics in postgresql`.

**Why this type:** the change ships player-visible behaviour (`/stats`, the per-map leaderboard) and the new module that stores it.
The module is infrastructure of the feature, not a feature of its own, so the scope is `stats`, not `persistence`. The module
is named `voyager-persistence` as the design says.

**Decision records:**
- `docs/decisions/0037-player-stats-on-postgresql.md` (MADR, Proposed). It records PostgreSQL for stats instead of MariaDB, and
  it supersedes the MariaDB choice in the greenfield design's "Persistence" section for the rebuild. The legacy tree keeps MariaDB.
- Decision D16 (avaje-config) is in `openspec/changes/adopt-avaje-config`. This change depends on it.

**Decisions changed in the greenfield design** (`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`), recorded as
decision rows D15 and D16 in its table (task 6.4):
- Persistence section: MariaDB and Hibernate `ON DUPLICATE KEY` become PostgreSQL and `ON CONFLICT`; `DATETIME(6)`, `INT UNSIGNED`
  and `rewriteBatchedStatements` become their PostgreSQL forms. The rest of the section stands: `StatelessSession`, no associations,
  Flyway owns the schema, hand-written repositories, single writer, `void` submits.
- Configuration section: the database keys are read through the loader of `adopt-avaje-config`.

**Depends on:** `adopt-avaje-config` (must merge first). Also `com-racers` (planned, branch `openspec/com-racers`, not merged):
this change does not store computer racers, which matches that proposal's rule "no stored record".

**Slices touched:** `api/stats` (new: records, ports, exception), `api/config` (database settings and `Secret`), `persistence` (new
module), `platform/stats` (new: observation of race and cup events, the non-blocking hand-off, the read hand-off to the tick), `platform/cup`
(calls the port at race end and at cup end), `platform/flight` and `platform/cup` (counters for boosts and resets, flight time),
`platform/text` (messages), `server` (settings, configuration check, composition root wiring, commands), `fitness` (module rules).
The race domain (`race`) gets no new dependency. Physics is not touched. Rings, scoring and the catalogue format are not touched.

**Out of scope:** importing the legacy MariaDB data (greenfield decision D11), a web or Discord leaderboard, cross-server statistics,
player data export or erasure tooling (the schema makes it one `DELETE` by `player_id`, the tooling is a later change), storing
computer racers, per-cup leaderboards (a query later), changes to the legacy tree, and the stats of tuning revisions.

## Why

The owner wants player statistics collected from now on: cup results and medals, per-run details (rings passed, boosts, resets,
split times), per-map best times with leaderboards, and a profile (cups played, wins, total flight time) that `/stats` shows. The
greenfield design already has a schema for most of it (`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`,
"Entity model"), written for MariaDB, and gates the whole persistence stage on E5. Data that is not collected from now on cannot be
recovered later, so collection starts with this change even though the reads are small.

The owner also decided that the catalogues (maps and cups) stay as files in git. The database holds identities and results, not
geometry (design "Entity model").

## What Changes

- **Records and ports (`api`).** Immutable completion records (`RaceCompletion`, `RacerRun`, `CupCompletion`, `CupRacerResult`) and
  two ports: `RaceRecorder` (write, `void`, never throws to the caller) and `StatsReader` (read, returns records). Both are plain
  interfaces, not sealed, because a port exists to be reimplemented (design, "Repositories").
- **Observation (`platform`).** At the end of each map the cup builds one `RaceCompletion` from the race's own values: each run's
  passed-ring ticks, its finish, its `MapScore` (ring points, placement bonus, medal). Three counters that the race domain does not
  keep are added in the platform: boosts accepted (`FireworkBoostTracker`), resets (`RunResetter`), and gliding ticks per racer
  (flight time = ticks x 50 ms). At the end of the cup the cup's standings become one `CupCompletion`.
- **Storage (`persistence`).** PostgreSQL schema in one Flyway migration, `V1__baseline.sql`, in schema `voyager`. Hibernate 7 with
  `StatelessSession`; entities with raw foreign keys and no associations; HQL constructor expressions for reads; plain JDBC
  upserts where the design asks for an atomic statement. One single-writer thread drains a bounded queue (see design, "Write path").
- **Reads.** `/stats [player]` and `/leaderboard <map>` (command names are proposals, see Q4). Reads run on a read executor; the reply
  is queued back to the tick and written there.
- **Configuration.** `VOYAGER_PERSISTENCE` (`off` or `required`), `VOYAGER_DB_URL`, `VOYAGER_DB_USER`, `VOYAGER_DB_PASSWORD` or
  `VOYAGER_DB_PASSWORD_FILE`, `VOYAGER_DB_POOL_SIZE`, `VOYAGER_DB_QUEUE_CAPACITY`. Checked by `ConfigCheck` for shape only; it does not
  connect (a check must be repeatable with no network).
- **Documentation.** The CloudNet guide's table of node-provided values gets the database variables; a reference page; a local run guide
  with a PostgreSQL compose file under `docker/postgres/`; ADR-0037.
- **Fitness.** The module is added to `FitnessCoverageTest` and `voyager/fitness/build.gradle.kts`; the module rules are in the design.

## Capabilities

### New Capabilities
- `persistence/postgres-storage`: the schema, the migration, the write path, the DB-down behaviour, the integration test rules.
- `stats/player-stats`: what is recorded per run, per cup, per map and per player; `/stats` and `/leaderboard`; the bot exclusion.

### Modified Capabilities
- `server/configuration`: gains the database keys (depends on `adopt-avaje-config`).
- `architecture/architecture-enforcement`: gains the persistence module and the rules of this change (design D12 of the change).

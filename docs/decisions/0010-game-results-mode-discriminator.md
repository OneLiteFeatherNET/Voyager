# ADR-0010: Single game_results table with mode discriminator and new map_personal_best table

## Status

Accepted

## Date

2026-04-25

## Decision makers

- Voyager server team
- Voyager database owner

## Context and problem statement

The existing `game_results` table holds one row per finished Race Mode session per player. Practice Mode (see [ADR-0004](0004-practice-mode-replaces-tutorial.md)) introduces two new persistence requirements:

- Each completed Practice attempt writes a row with the elapsed time and the awarded medal tier, but with no position and no position bonus.
- Each player has a single personal-best row per lesson (`map_name`), updated whenever a new attempt beats it.

Most leaderboard and analytics queries are mode-agnostic ("how many sessions did this player complete this week", "what is the average duration per map"). Splitting `game_results` into `race_results` and `practice_results` would force a `UNION ALL` for every aggregate query.

`ScoringService` removal is tracked in [ADR-0007](0007-scoring-strategy-pattern.md) and is out of scope here.

## Decision drivers

- Aggregate queries should not require `UNION` across two tables
- Mode is a low-cardinality discriminator (two values today), well suited to a single-table layout
- Personal-best lookups are point reads keyed by `(player_id, map_name)` and warrant their own table
- Enum storage must survive enum reordering and remain readable in database dumps
- The persistence layer must not import from `server/` (CLAUDE.md, Module Isolation)

## Considered options

- Option A: Add a `gameMode` column to the existing `game_results` table; add a separate `map_personal_best` table for Practice personal bests
- Option B: Use JPA `@Inheritance(strategy = SINGLE_TABLE)` with a `RaceResult` and `PracticeResult` entity hierarchy on `game_results`
- Option C: Split into `race_results` and `practice_results` tables

## Decision outcome

Chosen option: **Option A — single `game_results` table with `gameMode` discriminator plus a new `map_personal_best` table**, because aggregate queries stay single-table, personal-best reads stay point-keyed, and the schema avoids Hibernate inheritance metadata for what is a simple discriminator column.

The `gameMode` column is stored with `@Enumerated(EnumType.STRING)` and is indexed for filtered queries. `EnumType.STRING` survives reordering of the enum constants and remains human-readable in `mysqldump` output. The enum type itself comes from `shared/common` (see [ADR-0006](0006-gamemode-enum-in-shared-common.md)).

Practice Mode rows in `game_results` carry `position = 0` and `positionBonus = 0` and add a `medalTier` column (nullable for Race rows). The `map_personal_best` table is independent, keyed by `(player_id, map_name)`, and holds the best duration, best medal tier, and the achievement timestamp.

### Schema additions

`game_results` gains:

| Column | Type | Notes |
|---|---|---|
| `gameMode` | `VARCHAR(16)` | `EnumType.STRING`, indexed, `NOT NULL` |
| `medalTier` | `VARCHAR(16)` | `EnumType.STRING`, nullable; populated for Practice rows only |

New table `map_personal_best`:

| Column | Type | Notes |
|---|---|---|
| `player_id` | `BINARY(16)` | Part of composite primary key |
| `map_name` | `VARCHAR(64)` | Part of composite primary key |
| `best_duration_millis` | `BIGINT` | `NOT NULL` |
| `best_medal` | `VARCHAR(16)` | `EnumType.STRING`, `NOT NULL` |
| `achieved_at` | `TIMESTAMP` | `NOT NULL` |

### Consequences

- Good, because mode-agnostic aggregates stay single-table — no `UNION` for "sessions this week" or "average duration per map".
- Good, because the `gameMode` index makes mode-filtered queries selective.
- Good, because `map_personal_best` is a small point-read table with a natural composite key.
- Good, because `EnumType.STRING` survives enum reordering and is readable in dumps.
- Bad, because `medalTier` is nullable in `game_results`. The constraint that it is non-null exactly when `gameMode = 'PRACTICE'` is application-enforced, not database-enforced. A `CHECK` constraint can be added if MariaDB version permits.
- Bad, because `position` and `positionBonus` are zero rather than null for Practice rows. Reports that average position must filter by `gameMode = 'RACE'` to exclude these zeros.
- Neutral, because the migration is additive — no existing column or row changes type or value.

### Confirmation

- `GameResult` entity has a `@Enumerated(EnumType.STRING) @Column(name = "gameMode", nullable = false) GameMode gameMode` field.
- The `gameMode` column has a database index named `idx_game_results_gameMode`.
- `MapPersonalBest` entity exists and uses a composite key on `(player_id, map_name)`.
- `MapPersonalBestRepository` exposes `findByPlayerAndMap(UUID, String)` and `upsertIfBetter(MapPersonalBest)`.
- A Hibernate or Flyway migration script adds the two columns to `game_results` and creates `map_personal_best`.
- No file under `shared/database/src/main/` imports from `server/` or `plugins/game/`.

## Pros and cons of the options

### Option A — single table with discriminator plus separate personal-best table

- Good, because mode-agnostic queries stay single-table.
- Good, because the schema change is purely additive.
- Good, because personal-best reads use a small dedicated table.
- Bad, because the `medalTier` column is nullable for Race rows, and the conditional `NOT NULL` rule is application-level.

### Option B — JPA SINGLE_TABLE inheritance

- Good, because nullable columns become a Hibernate-managed concern via the discriminator.
- Bad, because it adds Hibernate inheritance metadata (`@DiscriminatorColumn`, `@DiscriminatorValue`, entity hierarchy) for what is functionally a single column.
- Bad, because every query must either operate on the base entity or specify the subtype, complicating repository code.
- Bad, because the discriminator column is functionally the same as Option A's `gameMode` column, so the inheritance overhead buys nothing.

### Option C — split into race_results and practice_results

- Good, because each table has zero nullable mode-specific columns.
- Bad, because every aggregate query needs `UNION ALL` across both tables.
- Bad, because repository code roughly doubles (two interfaces, two implementations, two query sets).
- Bad, because adding a future mode adds yet another table and yet another `UNION` branch.

## More information

- Related: [ADR-0004: Practice Mode replaces Tutorial Mode](0004-practice-mode-replaces-tutorial.md)
- Related: [ADR-0005: GameMode enum session discriminator](0005-gamemode-enum-session-discriminator.md)
- Related: [ADR-0006: GameMode enum in shared/common](0006-gamemode-enum-in-shared-common.md)
- Related: [ADR-0007: Scoring uses the Strategy pattern](0007-scoring-strategy-pattern.md)
- Hibernate enum mapping: `@Enumerated(EnumType.STRING)`

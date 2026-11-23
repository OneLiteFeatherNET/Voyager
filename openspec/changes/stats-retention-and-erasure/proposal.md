# Proposal

**Type and scope:** `docs(persistence)`. The change ships documentation only: an operator runbook for exporting and erasing one
player's statistics, a retention period, and a named owner of requests. Vertical slices: the persistence adapter (storage and
its schema) and the stats slice (`stats/player-stats`, which the erasure affects). No production code. Breaking: no.

## Why

Player statistics are personal data: each row is keyed by the player's UUID and the player's last known name is stored in
`voyager.player` (`voyager/persistence/src/main/resources/db/migration/V1__baseline.sql:5-10`). The server writes these rows
to PostgreSQL (`voyager/persistence/src/main/java/net/elytrarace/voyager/persistence/StatsWriter.java`). ADR-0037 keeps records
"indefinitely" and defers the retention purge and the erasure tooling to a follow-up change (`docs/decisions/0037-player-stats-on-postgresql.md:70-71`,
status Proposed at `:5`). The archived player-stats design says the same (`openspec/changes/archive/2026-10-14-player-stats/design.md:327-329`).

The archived proposal says erasure is "one `DELETE` by `player_id`" (`openspec/changes/archive/2026-10-14-player-stats/proposal.md:31`).
That is not what the schema allows. Four tables reference `voyager.player` by foreign key (`player_statistics`, `race_result`,
`cup_result`, `map_personal_best`), and a fifth, `race_split`, is reached through `race_result`. `map_personal_best` also references `race_result`, so
the rows must be removed in dependency order inside one transaction. A single delete would fail on the first foreign key.

Players in the EU can ask for erasure, and no procedure exists yet. This is a gap, not a legal assessment. Volume is small
today, which makes a manual, rehearsed procedure cheap now and a rushed one expensive later.

## What Changes

- A new operator runbook, `docs/guides/how-to-export-and-erase-player-statistics.md` (Diataxis: how-to), with: the export query by
  `player_id`; the erasure transaction in foreign-key order; the list of rows and tables it removes; its effect on personal bests
  and leaderboards; and the retention purge for rows older than the retention period.
- A defined retention period, written in the runbook and in ADR-0037's addendum. The period is an owner decision (open question).
- A named request owner in the runbook, and the channel on which a request is accepted and answered.
- The erasure procedure is rehearsed on a copy of the production schema before the runbook is marked usable, and the row counts of
  the rehearsal are recorded in the runbook.
- No code and no schema change. The erasure is a manual transaction run by an operator against the database.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `persistence/postgres-storage`: ADDED requirements that an operator can export and erase one player's records by `player_id`,
  that the erasure removes every row that references the player, and that a retention period is defined and applied.

## Trade-offs

### Pros

- Closes the exposure before volume grows, with no code and no release.
- The procedure is written against the real foreign keys, so it cannot silently fail on the first run.
- A rehearsal on a copy gives a measured row count before any real request.

### Cons

- Manual. Every request costs operator time, and a mistake in the transaction is only as safe as the rehearsal.
- Erasure removes history that leaderboards depend on: a deleted player's personal bests disappear, and the next best time of
  each map moves up. The runbook says so, and the owner accepts it per request or chooses pseudonymisation instead (open question).
- Retention purges remove the rows that per-cup and history views read. Only the period the owner sets is safe to purge.

## Out of scope

- A code tool or an admin command for erasure. The schema makes that a later change, and the runbook is the first cut.
- A legal assessment of GDPR or any other regime. The owner decides the basis and the response time.
- A player-facing self-service export or deletion.
- Pseudonymisation of past records. It is listed as an alternative in the open questions and is not built here.
- Changes to the legacy tree and to ADR-0037's decision on storage.

## Open questions for the owner

- Retention period: how long are race and cup rows kept, and does a purge remove them or only the player's name?
- Request owner and channel: who receives an erasure request, within how many days it is answered, and by which channel?
- Erasure or pseudonymisation: should a request replace the `player_id` with a tombstone UUID, which keeps leaderboards and
  aggregates intact, rather than deleting the rows?

# Tasks

Commit type: `docs(persistence)` for everything in this change. There is no production code, so no Red/Green pair: the check for
each behaviour is the rehearsal recorded in the runbook (section 3). The pull request contains no file under `openspec/`, and no
task is ticked in it.

## 1. Confirm the schema

- [ ] 1.1 Read every file in `voyager/persistence/src/main/resources/db/migration/` and list each table with a column `player_id`
  and its foreign key (`docs`)
- [ ] 1.2 Run the `information_schema` query that lists every `player_id` column on the rehearsal copy and compare it with 1.1 (`docs`)

## 2. Write the runbook

- [ ] 2.1 `docs/guides/how-to-export-and-erase-player-statistics.md`: prerequisites, the export queries per table, the erasure
  transaction in the order of design D2, and the statement of effect on bests and leaderboards (`docs`)
- [ ] 2.2 Add the purge query for the retention period, only once the owner has decided the period (`docs`)
- [ ] 2.3 Name the request owner role, the channel and the answer time in the runbook (`docs`)
- [ ] 2.4 Link the runbook from `docs/reference/database.md` (`docs`)

## 3. Rehearse on a copy

- [ ] 3.1 Restore a copy of the schema (backup or Flyway from the same migrations) and seed one test player with rows in every table
  (`docs`)
- [ ] 3.2 Run the export for the test player and record the row counts (`docs`)
- [ ] 3.3 Run the erasure and record the row count per table before and after; confirm `race_session` and `cup_session` counts did
  not change (`docs`)
- [ ] 3.4 Record the rehearsal in the runbook's rehearsal section with the schema version and the player used (`docs`)

## 4. Owner decisions and ADR addendum

- [ ] 4.1 Owner decides the retention period, the request owner, and deletion or tombstone pseudonymisation (open questions in the
  proposal). Recorded in the runbook (`docs`)
- [ ] 4.2 Add a dated addendum to `docs/decisions/0037-player-stats-on-postgresql.md` that names the retention period and points to
  the runbook; Status stays Proposed (`docs`)

## 5. Open the code pull request

- [ ] 5.1 Open the pull request under `docs(persistence): add runbook for erasing player statistics`. It contains no file under
  `openspec/`, and no task is ticked in it.

# Design

## Context

The statistics schema is in `voyager/persistence/src/main/resources/db/migration/` (Flyway: `V1__baseline.sql`, `V2__catalogue_keys.sql`).
Every table that holds a player's data references `voyager.player (player_id)`, directly or through another table:

| Table | Player link | References |
|---|---|---|
| `player` | `player_id` (primary key); `last_known_name` is personal | none |
| `player_statistics` | `player_id` (primary key, foreign key) | `player` |
| `race_result` | `player_id` | `player`, `race_session` |
| `race_split` | through `race_result_id` | `race_result` |
| `cup_result` | `player_id` | `player`, `cup_session` |
| `map_personal_best` | `player_id`; also `race_result_id` | `player`, `race_result` |
| `race_session`, `cup_session` | none | `cup_session` (for `race_session`) |

A single `DELETE FROM voyager.player` therefore fails while any referencing row exists. The erasure must delete from the leaves
inward: `map_personal_best` first (it references `race_result`), then `race_split`, `race_result`, `cup_result`,
`player_statistics`, and last `player`.

## Goals and non-goals

- Goal: an operator can export and erase one player's rows with a written, rehearsed procedure, and can purge rows older than
  a defined retention period.
- Non-goal: code, a command, a schema change, a legal assessment, a self-service path for players.

## Decisions

### D1. The runbook is a how-to guide in docs/guides

`docs/guides/how-to-export-and-erase-player-statistics.md` follows the Diataxis how-to form: prerequisites, the export queries,
the erasure transaction, the purge query, the rehearsal record and the request owner. It is linked from
`docs/reference/database.md`.

### D2. Erasure is one transaction in foreign-key order

The erasure is a single `BEGIN ... COMMIT` block with the deletes in the order given in Context (`map_personal_best` first), and a `RAISE` or a `ROLLBACK`
on any row count the rehearsal did not predict. No `ON DELETE CASCADE` is added, because a schema change is out of scope.

### D3. Sessions are kept

`race_session` and `cup_session` carry counts, not player keys. They stay, so the history of the server's play is kept and
only the player's rows go.

### D4. Leaderboards are stated, not repaired

Removing `map_personal_best` rows moves the next best time of each layout up. The runbook states this before the erasure. No
recomputation is added.

### D5. Rehearsal on a copy is a gate

The runbook is not marked usable until a rehearsal on a copy of the production schema records, per table, the row count before
and after. The copy is restored from a backup or built by Flyway from the same migrations and seeded with one test player.

### D6. Retention and erasure are one procedure family, decided by the owner

The retention period and the choice between deletion and tombstone pseudonymisation (open questions in the proposal) are the
owner's. The runbook carries the chosen option; until then it states that retention is undecided and gives no purge query.

## Module map and dependency direction

No code module changes. The runbook names the tables of `voyager-persistence` and the effect on `stats/player-stats`. The
direction is unchanged: `voyager-persistence` remains an adapter, and the database is operated outside the game.

## Fitness

No code, so no new ArchUnit rule. The existing persistence rules in `voyager-fitness` are unchanged.

## Test plan

The Red-Green cycle does not apply: there is no production code. The check is the rehearsal (D5), recorded in the runbook, and the
migration files are read to confirm the table list. Rehearsal output is the only evidence, and it is not a unit test.

## Architecture decision

No new ADR. ADR-0037 gets an addendum that names the retention period and points to the runbook (task 4.2). Its Status stays Proposed
until the owner moves it.

## Risks

- A wrong table list leaves personal data behind. Mitigation: the table list is derived from the migrations and checked by a query
  against `information_schema` in the rehearsal, which lists every column named `player_id`.
- An erasure in the wrong order fails and leaves nothing removed. Mitigation: one transaction.
- A later migration adds a referencing table. Mitigation: the runbook says to re-run the `information_schema` query whenever a
  migration is added.

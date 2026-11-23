# Proposal

**Type and scope:** `feat(server)`. The change adds live health numbers to the game server: tick duration, particle packets and
the statistics writer's counters. It touches the server composition root (tick scheduling and the `/race` view), the platform
slice where the window and the counter live, and the configuration reference. Ships as code under `feat(server)`. Breaking: no.
No new dependency.

## Why

A public launch runs the game for real players on one node, and nothing on the node says how the tick is doing. The rebuild has
no metrics library in its main sources: no Micrometer, no Prometheus, no MSPT figure. The only timing in the platform is a wait
for in-flight chunk loads (`voyager/platform/src/main/java/net/elytrarace/voyager/platform/world/InFlightChunkLoads.java:48-55`),
which is not a tick measure. Logging is console-only (`voyager/server/src/main/resources/log4j2.xml:15-31`).

The statistics writer counts what it writes, retries and drops (`voyager/persistence/src/main/java/net/elytrarace/voyager/persistence/StatsWriter.java:32-35`),
and it exposes them through accessors (`StatsWriter.java:118-133`). No production code calls those accessors, so an operator cannot
see a drop. The in-memory queue is 256 deep (`StatsWriter.java:31`, `voyager/api/.../config/DatabaseConfig.java:36`), and the
shutdown drain is capped at ten seconds (`voyager/server/src/main/java/net/elytrarace/voyager/server/persistence/StatsBinding.java:94`).
A full queue or a late drain loses statistics silently today.

The configuration reference also lacks the database keys: `docs/reference/configuration.md` has no `VOYAGER_PERSISTENCE` or
`VOYAGER_DB_*` row, although `server/configuration` already requires every key to be documented.

## What Changes

- A rolling tick-duration summary, the mean and the maximum over a 600-tick window (30 s at 20 TPS), logged at INFO once per
  window.
- The same summary shown in `/race` (the cup status view, which takes no subcommand).
- A counter of particle packets sent per window, in the same INFO line and in `/race`.
- The statistics writer's counters `written`, `retried`, `droppedFull` and `droppedData` shown in the same INFO line and in `/race`.
- The database keys added to `docs/reference/configuration.md`, as `server/configuration` already requires.
- No new dependency and no exporter. Windows are counted in ticks, not wall-clock time, so the summary is testable.

## Capabilities

### New Capabilities

- `server/observability`: the tick summary, the packet counter, the writer counters, where they are shown, and the rule that
  reading them never delays a tick.

### Modified Capabilities

None. The documentation of database keys is already a requirement of `server/configuration` (`Environment names are documented`),
so this change fixes a gap against an existing requirement and adds no delta.

## Trade-offs

### Pros

- Makes tick stalls and packet budgets visible on the live node, from the logs and from `/race`, without an external system.
- Low cost: a window of three numbers, one log line every 30 seconds, and the existing counters.

### Cons

- Log volume grows by one INFO line every 30 seconds. Small, but it is a line on the console every half minute.
- The summary does not prevent loss. A full queue still drops the newest completion. Spilling to a file is out of scope and is
  the follow-up.
- A mean over 30 seconds hides a short stall that the maximum shows only when it is in the window.

## Out of scope

- An exporter (Prometheus, OpenTelemetry, Grafana) and any dashboard. The node has no such stack in this change.
- Spill-to-file or a retry store for the statistics queue. Listed as a follow-up.
- Per-player or per-cup metrics.
- Changing the queue capacity, the drain time or the retry policy.
- Alerting.

## Open questions for the owner

- Is 30 seconds the right window for the INFO line, or should the operator set it?
- Should `/race` show the summary to every sender or only to staff, given that it is currently open to every sender?

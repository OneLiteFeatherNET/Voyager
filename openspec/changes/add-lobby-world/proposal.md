# Proposal

**Conventional Commits:** `feat(race)`. Squash-merge PR title: `feat(race): add a dedicated lobby world for waiting racers`.

**Supersedes:** ADR-0022 (`docs/decisions/0022-waiting-lobby-in-first-map.md`, Proposed). That record rejected a lobby
world; the owner decided on 2026-10-12 that waiting racers use one. The new record is ADR-0029
(`docs/decisions/0029-dedicated-lobby-world-for-waiting-racers.md`). ADR-0022 is marked superseded by a task in this
change, not by an edit made now.

**Overrides in `openspec/specs/race/waiting-lobby/spec.md`:** the requirement "Waiting racers stand on the first map's
spawn without flight equipment" and its scenario "No world is created for the waiting room", which says
"THE SYSTEM SHALL NOT create a lobby world".

**Depends on:** `docs/openspec-boot-world-scope` (commit `4bf1b99`, not yet on `main`). That commit adds the boot-check
rule "at most one world is open during the check", which this change reuses for the lobby world. The code fix for #297
(`84a5675`) is already on `main`. If the docs commit is not merged first, this change restates the rule in
`specs/server/lobby-world`.

Slices touched: `race/flow` (the start gate), `platform/lobby` (the waiting room and its boss bar), `platform/text`
(the join line and boss bar wording), `platform/cup` (one new method on `CupSession` for the full-lobby countdown),
`platform/world` (the shared arrival step extracted from `MapTransition`), `api/race` (the lobby definition),
`server` (settings, configuration check, boot wiring). The rings, the cup scoring and the map format are not touched.

## Why

Racers wait on the first map's spawn today (ADR-0022). That puts a waiting player in a race world with the rings
and the cup's own state, and makes the first map the only place that can be seen before a race starts. The old game
gathered players in a flat lobby instance, and the owner wants the same: a lobby where players wait, see who is
coming, and see the countdown without flying. The rebuild has no lobby asset yet, so this change defines how one is
named, loaded and used, and records the operator task of converting the candidate world.

## What Changes

- **Lobby world.** Waiting racers stand in a dedicated lobby world, not in the first map's world. The world is named by
  the setting `VOYAGER_LOBBY_WORLD` with its spawn in `VOYAGER_LOBBY_SPAWN`. Both are optional. Without them the
  waiting room falls back to today's behaviour and logs a warning (design D2).
- **Lobby transfers.** A racer moves from the lobby world to the first map when the start countdown reaches its three
  digits, as now. A racer returns to the lobby world when a cup finishes. Map-to-map transitions are unchanged.
- **Join line.** Each join is announced in chat as `<name> joined the game (x/y)`, where x is the online count and y the
  maximum racer count. The wording comes from the old game's key `phase.lobby.player.join`.
- **Maximum racers.** `VOYAGER_MAX_PLAYERS` sets y. It defaults to 16 (owner decision OQ1). It is a soft threshold: it
  does not reject joins, because the late-joiner rule still holds.
- **Full-lobby countdown.** When the online count reaches the maximum during the countdown, the countdown shortens to
  10 seconds. It never lengthens, and it does not change once the start is committed (the last three seconds).
- **Boss bar.** A boss bar, not a scoreboard, shows the count and the countdown for as long as a racer is in the lobby
  world. The waiting and countdown lines leave the action bar. Chat keeps the join and cancel lines.
- **Boot.** The lobby world is loaded at boot and held for the life of the server. The boot configuration check covers it,
  with one world open at a time.
- **Operator task.** The candidate `ElytraLobby` world is upgraded to Minecraft 26.2 by the vanilla server, as the
  converted maps were. The source under `/mnt/projects/lab` is read-only, so the work is done on a copy.

**BREAKING (configuration):** none for existing settings. A deployment that does not set `VOYAGER_LOBBY_WORLD` keeps
today's behaviour, with a warning. The waiting-lobby requirements listed above change, and their replacements are in
the delta spec.

Out of scope: elytra and rockets in the lobby (the owner chose no flight there); a hard cap on racers (it would
contradict the late-joiner rule and needs its own change); a configurable full-lobby countdown (the owner decided a 10 s constant, OQ3); a grace period
for a racer who reconnects during the countdown; a per-map time limit; spectator mode; any change to the tree being
replaced; the conversion of `ElytraLobby` itself (an operator task, see tasks.md).

## Capabilities

### New Capabilities
- `server/lobby-world`: the setting that names the lobby world and its spawn, the fallback without one, the boot load
  and configuration check of that world, and the maximum racer setting.

### Modified Capabilities
- `race/waiting-lobby`: where waiting racers stand, what they see (boss bar and join line), where a finished cup returns
  them, and the full-lobby countdown.
